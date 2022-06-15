package cortex.jobmaster.orion.service.domain.computer_vision

import cortex.api.job.JobType.{ CVEvaluate, CVModelImport, CVModelTrain, CVPredict }
import cortex.api.job.album.augmentation.RequestedAugmentation.Params
import cortex.api.job.album.augmentation.{ AugmentationSummary, AugmentationSummaryCell }
import cortex.api.job.album.common.{ Image, Tag, TagArea }
import cortex.api.job.computervision._
import cortex.api.job.{ JobRequest, computervision }
import cortex.api.job.table.{ ProbabilityClassColumn => CortexProbabilityClassColumn, _ }
import cortex.api.job.common.{ ConfusionMatrix => CortexConfusionMatrix, ConfusionMatrixCell => CortexConfusionMatrixCell, _ }
import cortex.common.future.FutureExtensions._
import cortex.jobmaster.jobs.job.CommonConverters.fromCortexClassReference
import cortex.jobmaster.jobs.job.computer_vision.{ ClassificationJob, LocalizationJob, _ }
import cortex.jobmaster.jobs.job.redshift_exporter.RedshiftExporterJob
import cortex.jobmaster.jobs.job.tabular.TableExporterJob
import cortex.jobmaster.jobs.time.JobTimeInfo
import cortex.jobmaster.modules.SettingsModule
import cortex.jobmaster.orion.service.domain.JobRequestPartialHandler.{ JobId, JobResult }
import cortex.jobmaster.orion.service.domain.{ JobRequestPartialHandler, TableConverters, WithAugmentation }
import cortex.scheduler.TaskScheduler
import cortex.task.StorageAccessParams.S3AccessParams
import cortex.task.TabularAccessParams.RedshiftAccessParams
import cortex.task.computer_vision.AutoencoderParams._
import cortex.task.computer_vision.LocalizationParams.{ ComposeVideoTaskParams, FeatureExtractorSettings }
import cortex.task.computer_vision.ModelImportParams.{ ModelImportTaskParams, ModelImportTaskResult }
import cortex.task.computer_vision.ParameterValue._
import cortex.task.computer_vision.{ AutoAugmentation, ClassificationParams, LocalizationParams, _ }
import cortex.task.data_augmentation.DataAugmentationParams.AugmentationType
import cortex.task.tabular_data.Table
import cortex.task.transform.common.{ Column, TableFileType }
import cortex.task.transform.exporter.redshift.RedshiftExporterModule
import cortex.{ CortexException, TaskParams, TaskResult }

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Try }

// scalastyle:off number.of.methods
class ComputerVisionService(
    classificationJob: ClassificationJob,
    localizationJob:   LocalizationJob,
    autoencoderJob:    AutoencoderJob,
    modelImportJob:    ModelImportJob,
    customModelJob:    CustomModelJob,
    tableExporterJob:  TableExporterJob,
    s3AccessParams:    S3AccessParams,
    modelsBasePath:    String,
    baseTablesPath:    String
)(implicit executionContext: ExecutionContext)
  extends JobRequestPartialHandler with WithAugmentation {

  private def prepareTags(imageTags: Seq[Tag]): Seq[LocalizationParams.Tag] = {
    imageTags.map(t => toLocalTag(t))
  }

  private def prepareAugmentationParams(params: AutoAugmentationParams): AutoAugmentation.AutoAugmentationParams = {
    AutoAugmentation.AutoAugmentationParams(
      augmentations           = params.augmentations.map(toLocalDAParams),
      bloatFactor             = params.getBloatFactor,
      generateSampleAlbum     = params.generateSampleAlbum,
      sampleAlbumTargetPrefix = params.getSampleAlbumTargetPrefix
    )
  }

  private def buildMlEntityFilePath(mlEntityId: String) = {
    modelsBasePath + "/" + mlEntityId + ".pth"
  }

  private def toLocalTag(t: Tag): LocalizationParams.Tag = {
    t.area match {
      case Some(TagArea(top, left, height, width)) => LocalizationParams.Tag(
        xMin  = left,
        xMax  = left + width,
        yMin  = top,
        yMax  = top + height,
        label = t.label
      )
      case None => throw new RuntimeException("Area is absent in tag")
    }
  }

  private def toCustomModelTag(t: Tag): CustomModelParams.Tag = {
    CustomModelParams.Tag(
      label = t.label,
      area  = t.area.map { a =>
        CustomModelParams.TagArea(
          top    = a.top,
          left   = a.left,
          height = a.height,
          width  = a.width
        )
      }
    )
  }

  private def toExternalTag(t: LocalizationParams.Tag): PredictedTag = {
    PredictedTag(
      Some(Tag(
        label = t.label,
        Some(TagArea(
          top    = t.yMin,
          left   = t.xMin,
          height = t.yMax - t.yMin,
          width  = t.xMax - t.xMin
        ))
      )),
      confidence = t.confidence.getOrElse(0.0)
    )
  }

  private def toExternalTag(t: CustomModelParams.Tag): PredictedTag = {
    PredictedTag(
      Some(Tag(
        label = t.label,
        area  = t.area.map { a =>
          TagArea(
            top    = a.top,
            left   = a.left,
            height = a.height,
            width  = a.width
          )
        }
      )),
      confidence = t.confidence.getOrElse(0.0)
    )
  }

  protected def transformConfusionMatrix(
    confusionMatrix: ClassificationParams.ConfusionMatrix
  ): CortexConfusionMatrix = {
    val translatedCells = confusionMatrix.confusionMatrixCells.map { c =>
      CortexConfusionMatrixCell(
        actualLabelIndex    = Some(c.actualLabelIndex),
        predictedLabelIndex = Some(c.predictedLabelIndex),
        value               = c.value
      )
    }
    CortexConfusionMatrix(translatedCells, confusionMatrix.labels)
  }

  protected def transformConfusionMatrix(
    confusionMatrix: LocalizationParams.ConfusionMatrix
  ): CortexConfusionMatrix = {
    val translatedCells = confusionMatrix.confusionMatrixCells.map { c =>
      CortexConfusionMatrixCell(
        actualLabelIndex    = c.actualLabelIndex,
        predictedLabelIndex = c.predictedLabelIndex,
        value               = c.value
      )
    }
    CortexConfusionMatrix(translatedCells, confusionMatrix.labels)
  }

  protected def transformConfusionMatrix(
    confusionMatrix: CustomModelParams.ConfusionMatrix
  ): CortexConfusionMatrix = {
    val translatedCells = confusionMatrix.confusionMatrixCells.map { c =>
      CortexConfusionMatrixCell(
        actualLabelIndex    = c.actualLabelIndex,
        predictedLabelIndex = c.predictedLabelIndex,
        value               = c.value
      )
    }
    CortexConfusionMatrix(translatedCells, confusionMatrix.labels)
  }

  // TODO would not have to define this (twice) if we had monads
  private def executeTaskAndHandleF[TP <: TaskParams, TR <: TaskResult, R](
    paramsTry: Try[TP]
  )(taskExecutor: TP => Future[TR])(resultHandler: TR => Future[R]): Future[R] =
    for {
      params <- paramsTry.toFuture
      taskResult <- taskExecutor(params)
      result <- resultHandler(taskResult)
    } yield result

  private def executeTaskAndHandle[TP <: TaskParams, TR <: TaskResult, R](
    paramsTry: Try[TP]
  )(taskExecutor: TP => Future[TR])(resultHandler: TR => R): Future[R] =
    executeTaskAndHandleF(paramsTry)(taskExecutor)(resultHandler andThen Future.successful)

  def prepareAugmentationSummary(
    augmentationSummary: Map[AugmentationType, Long],
    augmentationParams:  AutoAugmentationParams
  ): AugmentationSummary = {
    val cells = augmentationSummary.flatMap {
      case (name, count) =>
        augmentationParams.augmentations.filter {
          _.params match {
            case _: Params.RotationParams           => name == AugmentationType.Rotation
            case _: Params.ShearingParams           => name == AugmentationType.Shearing
            case _: Params.NoisingParams            => name == AugmentationType.Noising
            case _: Params.ZoomInParams             => name == AugmentationType.ZoomIn
            case _: Params.ZoomOutParams            => name == AugmentationType.ZoomOut
            case _: Params.OcclusionParams          => name == AugmentationType.Occlusion
            case _: Params.TranslationParams        => name == AugmentationType.Translation
            case _: Params.SaltPepperParams         => name == AugmentationType.SaltPepper
            case _: Params.MirroringParams          => name == AugmentationType.Mirroring
            case _: Params.CroppingParams           => name == AugmentationType.Cropping
            case _: Params.PhotometricDistortParams => name == AugmentationType.PhotometricDistort
            case _: Params.BlurringParams           => name == AugmentationType.Blurring
            case Params.Empty =>
              throw new RuntimeException("Requested augmentation cannot be empty")
          }
        }.map { augmentation =>
          AugmentationSummaryCell(
            requestedAugmentation = Some(augmentation),
            imagesCount           = count
          )
        }
    }.toSeq

    AugmentationSummary(
      augmentationSummaryCells = cells
    )
  }

  // scalastyle:off
  def train(
    jobId:    JobId,
    request:  CVModelTrainRequest,
    testMode: Boolean             = false
  ): Future[(CVModelTrainResult, JobTimeInfo)] = {
    val modelType: Option[TLModelType.Type] = request.modelType.map(_.`type`)
    val filePathPrefix = request.filePathPrefix
    val imagesPath = request.images.map(_.getImage.filePath)
    val tuneFeatureExtractor = request.tuneFeatureExtractor
    val referenceIds = request.images.map(_.getImage.referenceId)
    val displayNames = Some(request.images.map(_.getImage.displayName))
    val featureExtractorClassReference = request.featureExtractorClassReference.getOrElse(
      throw new RuntimeException("Train request doesn't contain `featureExtractorClassReference` that is required")
    )
    val augmentationParams = request.augmentationParams.map(prepareAugmentationParams)
    val outputTableS3Path = request.probabilityPredictionTable.map {
      _ => generateOutputTableS3Path(jobId)
    }

    modelType match {
      case Some(TLModelType.Type.AutoencoderType(classReference)) =>
        val params = Try(
          AutoencoderTrainTaskParams(
            albumPath                      = filePathPrefix,
            imagePaths                     = imagesPath,
            referenceIds                   = referenceIds,
            modelsBasePath                 = modelsBasePath,
            outputS3Params                 = s3AccessParams,
            augmentationParams             = augmentationParams,
            featureExtractorClassReference = fromCortexClassReference(featureExtractorClassReference),
            featureExtractorParameters     = toParameterValuesMap(request.featureExtractorParameters),
            modelParameters                = toParameterValuesMap(request.modelParameters),
            classReference                 = fromCortexClassReference(classReference),
            testMode                       = testMode
          )
        )

        def handler(taskResult: AutoencoderTrainTaskResult): (CVModelTrainResult, JobTimeInfo) = {
          (CVModelTrainResult(
            cvModelReference          = Some(ModelReference(
              id       = taskResult.modelId,
              filePath = buildMlEntityFilePath(taskResult.modelId)
            )),
            featureExtractorReference = Some(ModelReference(
              id       = taskResult.featureExtractorId,
              filePath = buildMlEntityFilePath(taskResult.featureExtractorId)
            )),
            reconstructionLoss        = Some(taskResult.reconstructionLoss),
            augmentationSummary       = taskResult.augmentationResult.map { s =>
              prepareAugmentationSummary(
                s.augmentationSummary,
                request.augmentationParams.getOrElse(
                  throw new RuntimeException("Result has augmentations that were not requested")
                )
              )
            },
            dataFetchTime             = taskResult.dataFetchTime,
            trainingTime              = taskResult.trainingTime,
            saveModelTime             = taskResult.saveModelTime + taskResult.saveFeatureExtractorTime,
            predictionTime            = taskResult.predictionTime
          ), JobTimeInfo(Seq(taskResult.taskTimeInfo)))
        }

        executeTaskAndHandle(params)(autoencoderJob.train(jobId, _))(handler)
      case Some(TLModelType.Type.ClassifierType(classReference)) =>
        val params = Try(
          ClassificationParams.CVTrainTaskParams(
            albumPath                      = filePathPrefix,
            imagePaths                     = imagesPath,
            labels                         = request.images.map(_.tags.head.label),
            tuneFeatureExtractor           = tuneFeatureExtractor,
            referenceIds                   = referenceIds,
            displayNames                   = displayNames,
            modelsBasePath                 = modelsBasePath,
            featureExtractorId             = request.featureExtractorId,
            featureExtractorClassReference = fromCortexClassReference(featureExtractorClassReference),
            featureExtractorParameters     = toParameterValuesMap(request.featureExtractorParameters),
            modelParameters                = toParameterValuesMap(request.modelParameters),
            classReference                 = fromCortexClassReference(classReference),
            outputS3Params                 = s3AccessParams,
            augmentationParams             = augmentationParams,
            outputTableS3Path              = outputTableS3Path,
            testMode                       = testMode
          )
        )

        def handler(taskResult: ClassificationParams.CVTrainTaskResult): (CVModelTrainResult, JobTimeInfo) = {
          (CVModelTrainResult(
            cvModelReference                 = Some(ModelReference(
              id       = taskResult.modelId,
              filePath = buildMlEntityFilePath(taskResult.modelId)
            )),
            featureExtractorReference        = Some(ModelReference(
              id       = taskResult.featureExtractorId,
              filePath = buildMlEntityFilePath(taskResult.featureExtractorId)
            )),
            images                           = taskResult.predictions.map(toPredictedImage),
            confusionMatrix                  = Some(transformConfusionMatrix(taskResult.confusionMatrix)),
            augmentedImages                  = taskResult.augmentationResult.map { r =>
              parseAugmentationResult(r.transformResult)
            }.getOrElse(Seq()),
            augmentationSummary              = taskResult.augmentationResult.map { s =>
              prepareAugmentationSummary(
                s.augmentationSummary,
                request.augmentationParams.getOrElse(
                  throw new RuntimeException("Result has augmentations that were not requested")
                )
              )
            },
            probabilityPredictionTableSchema = taskResult.predictionTable.map(toProbabilityPredictionTableSchema),
            dataFetchTime                    = taskResult.dataFetchTime,
            trainingTime                     = taskResult.trainingTime,
            saveModelTime                    = taskResult.saveModelTime + taskResult.saveFeatureExtractorTime,
            predictionTime                   = taskResult.predictionTime,
            pipelineTimings                  = taskResult.pipelineTimings
          ), JobTimeInfo(Seq(taskResult.taskTimeInfo)))
        }

        executeTaskAndHandle(params) { taskParams =>
          for {
            result <- classificationJob.train(jobId, taskParams)
            _ <- exportToDatabaseIfNeeded(
              jobId       = jobId,
              table       = request.probabilityPredictionTable,
              s3TablePath = taskParams.outputTableS3Path,
              columns     = result.predictionTable.map(toColumns)
            )
          } yield result
        }(handler)
      case Some(TLModelType.Type.LocalizerType(classReference)) =>
        val params = Try(
          LocalizationParams.TrainTaskParams(
            albumPath                      = filePathPrefix,
            imagePaths                     = imagesPath,
            modelsBasePath                 = modelsBasePath,
            outputS3Params                 = s3AccessParams,
            tags                           = request.images.map(image => prepareTags(image.tags)),
            referenceIds                   = referenceIds,
            displayNames                   = displayNames,
            featureExtractorClassReference = fromCortexClassReference(featureExtractorClassReference),
            modelParameters                = toParameterValuesMap(request.modelParameters),
            classReference                 = fromCortexClassReference(classReference),
            augmentationParams             = augmentationParams,
            outputTableS3Path              = outputTableS3Path,
            inputSize                      = request.inputSize.map(fromCortexInputSize),
            labelsOfInterest               = request.labelsOfInterest.map(_.label),
            thresholds                     = request.labelsOfInterest.map(_.threshold),
            defaultVisualThreshold         = request.defaultVisualThreshold,
            iouThreshold                   = request.iouThreshold,
            featureExtractorSettings       = FeatureExtractorSettings(
              tuneFeatureExtractor         = tuneFeatureExtractor,
              featureExtractorLearningRate = request.featureExtractorLearningRate,
              featureExtractorParameters   = toParameterValuesMap(request.featureExtractorParameters),
              featureExtractorId           = request.featureExtractorId
            ),
            modelLearningRate              = request.modelLearningRate,
            testMode                       = testMode
          )
        )

        def handler(taskResult: LocalizationParams.TrainTaskResult): (CVModelTrainResult, JobTimeInfo) = {
          (CVModelTrainResult(
            cvModelReference                 = Some(ModelReference(
              id       = taskResult.modelId,
              filePath = buildMlEntityFilePath(taskResult.modelId)
            )),
            featureExtractorReference        = Some(ModelReference(
              id       = taskResult.featureExtractorId,
              filePath = buildMlEntityFilePath(taskResult.featureExtractorId)
            )),
            images                           = taskResult.predictions.map(toPredictedImage),
            map                              = Some(taskResult.mAP),
            confusionMatrix                  = Some(transformConfusionMatrix(taskResult.confusionMatrix)),
            augmentedImages                  = taskResult.augmentationResult.map { r =>
              parseAugmentationResult(r.transformResult)
            }.getOrElse(Seq()),
            augmentationSummary              = taskResult.augmentationResult.map { s =>
              prepareAugmentationSummary(
                s.augmentationSummary,
                request.augmentationParams.getOrElse(
                  throw new RuntimeException("Result has augmentations that were not requested")
                )
              )
            },
            probabilityPredictionTableSchema = taskResult.predictionTable.map(toProbabilityPredictionTableSchema),
            dataFetchTime                    = taskResult.dataFetchTime,
            trainingTime                     = taskResult.trainingTime,
            saveModelTime                    = taskResult.saveModelTime + taskResult.saveFeatureExtractorTime,
            predictionTime                   = taskResult.predictionTime
          ), JobTimeInfo(Seq(taskResult.taskTimeInfo)))
        }

        executeTaskAndHandle(params) { taskParams =>
          for {
            result <- localizationJob.train(jobId, taskParams)
            _ <- exportToDatabaseIfNeeded(
              jobId       = jobId,
              table       = request.probabilityPredictionTable,
              s3TablePath = taskParams.outputTableS3Path,
              columns     = result.predictionTable.map(toColumns)
            )
          } yield result
        }(handler)
      case unknown =>
        Future.failed(new RuntimeException(s"Unknown model type $unknown"))
    }
  }

  def evaluate(jobId: JobId, request: EvaluateRequest): Future[(EvaluateResult, JobTimeInfo)] = {

    def getImagePaths: Seq[String] = request.images.map(_.getImage.filePath)
    val referenceIds = request.images.map(_.getImage.referenceId)
    val displayNames = Some(request.images.map(_.getImage.displayName))
    val outputTableS3Path = request.probabilityPredictionTable.map {
      _ => generateOutputTableS3Path(jobId)
    }

    def executeTLTask(tlModel: TLModel): Future[(EvaluateResult, JobTimeInfo)] = {
      val featureExtractorClassReference = tlModel.featureExtractorClassReference.getOrElse(
        throw new RuntimeException(
          "Evaluate request with TL Model doesn't contain `featureExtractorClassReference` that is required"
        )
      )
      tlModel.modelType.map(_.`type`) match {
        case Some(TLModelType.Type.AutoencoderType(classReference)) =>
          val params = Try(
            AutoencoderScoreTaskParams(
              modelId                        = request.modelId,
              albumPath                      = request.filePathPrefix,
              imagePaths                     = getImagePaths,
              modelsBasePath                 = modelsBasePath,
              outputS3Params                 = s3AccessParams,
              featureExtractorClassReference = fromCortexClassReference(featureExtractorClassReference),
              classReference                 = fromCortexClassReference(classReference)
            )
          )

          def handler(taskResult: AutoencoderScoreTaskResult): (EvaluateResult, JobTimeInfo) = {
            (EvaluateResult(
              map           = Some(taskResult.reconstructionLoss),
              dataFetchTime = taskResult.dataFetchTime,
              loadModelTime = taskResult.loadModelTime,
              scoreTime     = taskResult.scoreTime
            ), JobTimeInfo(Seq(taskResult.taskTimeInfo)))
          }

          executeTaskAndHandle(params)(autoencoderJob.score(jobId, _))(handler)
        case Some(TLModelType.Type.ClassifierType(classReference)) =>
          val params = Try(
            ClassificationParams.CVScoreTaskParams(
              albumPath                      = request.filePathPrefix,
              imagePaths                     = getImagePaths,
              modelId                        = request.modelId,
              labels                         = request.images.map(image => image.tags.head.label),
              referenceIds                   = referenceIds,
              displayNames                   = displayNames,
              featureExtractorClassReference = fromCortexClassReference(featureExtractorClassReference),
              classReference                 = fromCortexClassReference(classReference),
              modelsBasePath                 = modelsBasePath,
              outputS3Params                 = s3AccessParams,
              outputTableS3Path              = outputTableS3Path
            )
          )

          def handler(taskResult: ClassificationParams.CVScoreTaskResult): (EvaluateResult, JobTimeInfo) = {
            val predictedImages = taskResult.predictions.map { prediction =>
              PredictedImage(
                Some(Image(prediction.filename)),
                Seq(PredictedTag(Some(Tag(prediction.label, None)), prediction.confidence))
              )
            }
            (EvaluateResult(
              images                           = predictedImages,
              confusionMatrix                  = Some(transformConfusionMatrix(taskResult.confusionMatrix)),
              probabilityPredictionTableSchema = taskResult.predictionTable.map(toProbabilityPredictionTableSchema),
              dataFetchTime                    = taskResult.dataFetchTime,
              loadModelTime                    = taskResult.loadModelTime,
              scoreTime                        = taskResult.scoreTime,
              pipelineTimings                  = taskResult.pipelineTimings
            ), JobTimeInfo(Seq(taskResult.taskTimeInfo)))
          }

          executeTaskAndHandle(params) { taskParams =>
            for {
              result <- classificationJob.score(jobId, taskParams)
              _ <- exportToDatabaseIfNeeded(
                jobId       = jobId,
                table       = request.probabilityPredictionTable,
                s3TablePath = taskParams.outputTableS3Path,
                columns     = result.predictionTable.map(toColumns)
              )
            } yield result
          }(handler)
        case Some(TLModelType.Type.LocalizerType(classReference)) =>
          val params = Try(
            LocalizationParams.ScoreTaskParams(
              albumPath                      = request.filePathPrefix,
              imagePaths                     = getImagePaths,
              referenceIds                   = referenceIds,
              displayNames                   = displayNames,
              modelsBasePath                 = modelsBasePath,
              outputS3Params                 = s3AccessParams,
              tags                           = request.images.map(image => prepareTags(image.tags)),
              modelId                        = request.modelId,
              featureExtractorClassReference = fromCortexClassReference(featureExtractorClassReference),
              classReference                 = fromCortexClassReference(classReference),
              labelsOfInterest               = request.labelsOfInterest.map(_.label),
              thresholds                     = request.labelsOfInterest.map(_.threshold),
              defaultVisualThreshold         = request.defaultVisualThreshold,
              iouThreshold                   = request.iouThreshold,
              outputTableS3Path              = outputTableS3Path
            )
          )

          def handler(taskResult: LocalizationParams.ScoreTaskResult): (EvaluateResult, JobTimeInfo) = {
            (EvaluateResult(
              images                           = taskResult.predictions.map(toPredictedImage),
              map                              = Some(taskResult.mAP),
              confusionMatrix                  = Some(transformConfusionMatrix(taskResult.confusionMatrix)),
              probabilityPredictionTableSchema = taskResult.predictionTable.map(toProbabilityPredictionTableSchema),
              dataFetchTime                    = taskResult.dataFetchTime,
              loadModelTime                    = taskResult.loadModelTime,
              scoreTime                        = taskResult.scoreTime
            ), JobTimeInfo(Seq(taskResult.taskTimeInfo)))
          }

          executeTaskAndHandle(params) { taskParams =>
            for {
              result <- localizationJob.score(jobId, taskParams)
              _ <- exportToDatabaseIfNeeded(
                jobId       = jobId,
                table       = request.probabilityPredictionTable,
                s3TablePath = taskParams.outputTableS3Path,
                columns     = result.predictionTable.map(toColumns)
              )
            } yield result
          }(handler)
        case unknown =>
          Future.failed(new RuntimeException(s"Unknown model type $unknown"))
      }
    }

    def executeCustomTask(customModel: CustomModel): Future[(EvaluateResult, JobTimeInfo)] = {
      val classReference = customModel.classReference.getOrElse(
        throw new RuntimeException("Class reference must be specified for custom CV model")
      )
      val params = Try(
        CustomModelParams.ScoreTaskParams(
          modelId        = request.modelId,
          modelsBasePath = modelsBasePath,
          tags           = request.images.map(_.tags.map(toCustomModelTag)),
          albumPath      = request.filePathPrefix,
          imagePaths     = getImagePaths,
          classReference = fromCortexClassReference(classReference),
          outputS3Params = s3AccessParams
        )
      )

      def handler(taskResult: CustomModelParams.ScoreTaskResult): (EvaluateResult, JobTimeInfo) = {
        (EvaluateResult(
          images          = taskResult.predictions.map(toPredictedImage),
          map             = taskResult.mAP,
          confusionMatrix = Some(transformConfusionMatrix(taskResult.confusionMatrix)),
          dataFetchTime   = taskResult.dataFetchTime,
          loadModelTime   = taskResult.loadModelTime,
          scoreTime       = taskResult.scoreTime
        ), JobTimeInfo(Seq(taskResult.taskTimeInfo)))
      }

      executeTaskAndHandle(params)(customModelJob.score(jobId, _))(handler)
    }

    def executeTask(modelType: CVModelType.Type): Future[(EvaluateResult, JobTimeInfo)] = modelType match {
      case CVModelType.Type.TlModel(tlModel)         => executeTLTask(tlModel)
      case CVModelType.Type.CustomModel(customModel) => executeCustomTask(customModel)
      case unknown =>
        Future.failed(new RuntimeException(s"Unknown model type $unknown"))
    }

    for {
      modelType <- Try(request.getModelType.`type`).toFuture
      result <- executeTask(modelType)
    } yield result
  }

  private def toPredictedImage(p: ClassificationParams.PredictionResult): PredictedImage = {
    PredictedImage(
      Some(Image(p.filename)),
      Seq(PredictedTag(Some(Tag(p.label, None)), p.confidence))
    )
  }

  private def toPredictedImage(p: LocalizationParams.PredictionResult): PredictedImage = {
    val image = Image(p.filename)
    val tags = p.tags.map(toExternalTag)
    PredictedImage(Some(image), tags)
  }

  private def toPredictedImage(p: CustomModelParams.PredictionResult): PredictedImage = {
    val image = Image(p.filename)
    val tags = p.tags.map(toExternalTag)
    PredictedImage(Some(image), tags)
  }

  private def toPredictedImage(p: AutoencoderParams.PredictionResult): PredictedImage = {
    val image = Image(p.filename, Some(p.referenceId), Some(p.imageSize))
    PredictedImage(Some(image))
  }

  def predict(jobId: JobId, request: PredictRequest): Future[(PredictResult, JobTimeInfo)] = {
    val modelType = request.modelType.map(_.`type`)
    val filePathPrefix = request.filePathPrefix
    val imagePaths = request.images.map(_.filePath)
    val referenceIds = request.images.map(_.referenceId)
    val displayNames = Some(request.images.map(_.displayName))
    val outputTableS3Path = request.probabilityPredictionTable.map {
      _ => generateOutputTableS3Path(jobId)
    }

    def executeTLTask(tlModel: TLModel): Future[(PredictResult, JobTimeInfo)] = {
      val featureExtractorClassReference = tlModel.featureExtractorClassReference.getOrElse(
        throw new RuntimeException("Predict request doesn't contain `featureExtractorClassReference` that is required")
      )
      tlModel.modelType.map(_.`type`) match {
        case Some(TLModelType.Type.AutoencoderType(classReference)) =>
          val outputAlbumPath = request.targetPrefix.getOrElse(
            throw new RuntimeException("Request doesn't contain `targetPrefix` that is required for StackedAutoencoder")
          )
          val params = Try(
            AutoencoderPredictTaskParams(
              modelId                        = request.modelId,
              albumPath                      = filePathPrefix,
              imagePaths                     = imagePaths,
              referenceIds                   = referenceIds,
              modelsBasePath                 = modelsBasePath,
              outputS3Params                 = s3AccessParams,
              outputAlbumPath                = outputAlbumPath,
              featureExtractorClassReference = fromCortexClassReference(featureExtractorClassReference),
              classReference                 = fromCortexClassReference(classReference)
            )
          )

          def handler(taskResult: AutoencoderPredictTaskResult): (PredictResult, JobTimeInfo) = {
            (PredictResult(
              images         = taskResult.predictions.map(toPredictedImage),
              dataFetchTime  = taskResult.dataFetchTime,
              loadModelTime  = taskResult.loadModelTime,
              predictionTime = taskResult.predictionTime
            ), JobTimeInfo(Seq(taskResult.taskTimeInfo)))
          }

          executeTaskAndHandle(params)(autoencoderJob.predict(jobId, _))(handler)
        case Some(TLModelType.Type.ClassifierType(classReference)) =>
          val params = Try(
            ClassificationParams.CVPredictTaskParams(
              albumPath                      = request.filePathPrefix,
              imagePaths                     = request.images.map(_.filePath),
              referenceIds                   = referenceIds,
              displayNames                   = displayNames,
              modelId                        = request.modelId,
              featureExtractorClassReference = fromCortexClassReference(featureExtractorClassReference),
              classReference                 = fromCortexClassReference(classReference),
              modelsBasePath                 = modelsBasePath,
              outputS3Params                 = s3AccessParams,
              outputTableS3Path              = outputTableS3Path
            )
          )

          def handler(taskResult: ClassificationParams.CVPredictTaskResult): (PredictResult, JobTimeInfo) = {
            (PredictResult(
              images                           = taskResult.predictions.map(toPredictedImage),
              videoFileSize                    = None,
              probabilityPredictionTableSchema = taskResult.predictionTable.map(toProbabilityPredictionTableSchema),
              dataFetchTime                    = taskResult.dataFetchTime,
              loadModelTime                    = taskResult.loadModelTime,
              predictionTime                   = taskResult.predictionTime,
              pipelineTimings                  = taskResult.pipelineTimings
            ), JobTimeInfo(Seq(taskResult.taskTimeInfo)))
          }

          executeTaskAndHandle(params) { taskParams =>
            for {
              result <- classificationJob.predict(jobId, taskParams)
              _ <- exportToDatabaseIfNeeded(
                jobId       = jobId,
                table       = request.probabilityPredictionTable,
                s3TablePath = taskParams.outputTableS3Path,
                columns     = result.predictionTable.map(toColumns)
              )
            } yield result
          }(handler)
        case Some(TLModelType.Type.LocalizerType(classReference)) =>
          val imagesPaths = request.images.map(_.filePath)
          val params = Try(
            LocalizationParams.PredictTaskParams(
              albumPath                      = request.filePathPrefix,
              imagePaths                     = imagesPaths,
              referenceIds                   = referenceIds,
              displayNames                   = displayNames,
              modelId                        = request.modelId,
              featureExtractorClassReference = fromCortexClassReference(featureExtractorClassReference),
              classReference                 = fromCortexClassReference(classReference),
              modelsBasePath                 = modelsBasePath,
              outputS3Params                 = s3AccessParams,
              outputTableS3Path              = outputTableS3Path,
              labelsOfInterest               = request.labelsOfInterest.map(_.label),
              thresholds                     = request.labelsOfInterest.map(_.threshold),
              defaultVisualThreshold         = request.defaultVisualThreshold
            )
          )

          def handler(taskResult: LocalizationParams.PredictTaskResult): Future[(PredictResult, JobTimeInfo)] = {
            def composeVideo: Future[Option[LocalizationParams.ComposeVideoTaskResult]] =
              request.videoParams match {
                case Some(videoParams) =>
                  localizationJob.composeVideo(jobId, ComposeVideoTaskParams(
                    request.filePathPrefix,
                    imagePaths,
                    taskResult.predictions.map(_.tags.toSeq),
                    s3AccessParams,
                    videoParams.targetVideoFilePath,
                    videoParams.videoAssembleFrameRate,
                    videoParams.videoAssembleHeight,
                    videoParams.videoAssembleWidth,
                    request.labelsOfInterest.map(_.label)
                  )).map(Some(_))
                case None =>
                  Future.successful(None)
              }

            composeVideo.map { videoCreationResult =>
              val tasksTimeInfo = videoCreationResult.map(
                vcr => Seq(taskResult.taskTimeInfo, vcr.taskTimeInfo)
              ).getOrElse(Seq(taskResult.taskTimeInfo))
              (PredictResult(
                images                           = taskResult.predictions.map(toPredictedImage),
                videoFileSize                    = videoCreationResult.map(_.videoFileSize),
                probabilityPredictionTableSchema = taskResult.predictionTable.map(toProbabilityPredictionTableSchema),
                dataFetchTime                    = taskResult.dataFetchTime,
                loadModelTime                    = taskResult.loadModelTime,
                predictionTime                   = taskResult.predictionTime
              ), JobTimeInfo(tasksTimeInfo))
            }
          }

          executeTaskAndHandleF(params) { taskParams =>
            for {
              result <- localizationJob.predict(jobId, taskParams)
              _ <- exportToDatabaseIfNeeded(
                jobId       = jobId,
                table       = request.probabilityPredictionTable,
                s3TablePath = taskParams.outputTableS3Path,
                columns     = result.predictionTable.map(toColumns)
              )
            } yield result
          }(handler)
        case unknown =>
          Future.failed(new RuntimeException(s"Unknown model type $unknown"))
      }
    }

    def executeCustomTask(customModel: CustomModel): Future[(PredictResult, JobTimeInfo)] = {
      val classReference = customModel.classReference.getOrElse(
        throw new RuntimeException("Class reference must be specified for custom CV model")
      )
      val params = Try(
        CustomModelParams.PredictTaskParams(
          modelId           = request.modelId,
          modelsBasePath    = modelsBasePath,
          albumPath         = request.filePathPrefix,
          imagePaths        = imagePaths,
          referenceIds      = referenceIds,
          displayNames      = displayNames,
          classReference    = fromCortexClassReference(classReference),
          outputS3Params    = s3AccessParams,
          outputTableS3Path = outputTableS3Path
        )
      )

      def handler(taskResult: CustomModelParams.PredictTaskResult): (PredictResult, JobTimeInfo) = {
        (PredictResult(
          images                           = taskResult.predictions.map(toPredictedImage),
          probabilityPredictionTableSchema = taskResult.predictionTable.map(toProbabilityPredictionTableSchema),
          dataFetchTime                    = taskResult.dataFetchTime,
          loadModelTime                    = taskResult.loadModelTime,
          predictionTime                   = taskResult.predictionTime
        ), JobTimeInfo(Seq(taskResult.taskTimeInfo)))
      }

      executeTaskAndHandle(params) { taskParams =>
        for {
          result <- customModelJob.predict(jobId, taskParams)
          _ <- exportToDatabaseIfNeeded(
            jobId       = jobId,
            table       = request.probabilityPredictionTable,
            s3TablePath = taskParams.outputTableS3Path,
            columns     = result.predictionTable.map(toColumns)
          )
        } yield result
      }(handler)
    }

    modelType match {
      case Some(CVModelType.Type.TlModel(tlModel))         => executeTLTask(tlModel)
      case Some(CVModelType.Type.CustomModel(customModel)) => executeCustomTask(customModel)
      case unknown =>
        Future.failed(new RuntimeException(s"Unknown model type $unknown"))
    }
  }

  def importModel(jobId: JobId, request: CVModelImportRequest): Future[(CVModelImportResult, JobTimeInfo)] = {
    val params = request.modelType match {
      case Some(CVModelType(CVModelType.Type.TlModel(TLModel(Some(modelType), Some(feClassReference))))) =>
        Try(ModelImportTaskParams(
          modelPath                      = request.path,
          featureExtractorClassReference = Some(fromCortexClassReference(feClassReference)),
          classReference                 = fromCortexClassReference(getClassReference(modelType)),
          modelType                      = if (request.feOnly) None else Some(parseModelType(modelType)),
          modelsBasePath                 = modelsBasePath,
          outputS3Params                 = s3AccessParams
        ))

      case Some(CVModelType(CVModelType.Type.CustomModel(CustomModel(Some(classReference))))) =>
        Try(ModelImportTaskParams(
          modelPath                      = request.path,
          featureExtractorClassReference = None,
          classReference                 = fromCortexClassReference(classReference),
          modelType                      = Some("custom"),
          modelsBasePath                 = modelsBasePath,
          outputS3Params                 = s3AccessParams
        ))

      case Some(unknown) =>
        Failure(new RuntimeException(s"Unknown model type $unknown"))

      case None =>
        Failure(new RuntimeException("Model type is not defined"))

    }

    def handler(taskResult: ModelImportTaskResult): (CVModelImportResult, JobTimeInfo) = {
      (CVModelImportResult(
        cvModelReference          = taskResult.modelId.map { modelId =>
          ModelReference(
            id       = modelId,
            filePath = buildMlEntityFilePath(modelId)
          )
        },
        featureExtractorReference = taskResult.featureExtractorId.map { featureExtractorId =>
          ModelReference(
            id       = featureExtractorId,
            filePath = buildMlEntityFilePath(featureExtractorId)
          )
        }
      ), JobTimeInfo(Seq(taskResult.taskTimeInfo)))
    }

    executeTaskAndHandle(params)(modelImportJob.importModel(jobId, _))(handler)
  }

  private def exportToDatabaseIfNeeded(
    jobId:       String,
    table:       Option[TableMeta],
    s3TablePath: Option[String],
    columns:     Option[Seq[TableColumn]]
  ) = {
    (table, columns) match {
      case (Some(TableMeta(schema, name)), Some(tableColumns)) =>
        tableExporterJob.exportToTable(
          jobId    = jobId,
          table    = Table(
            schema = schema,
            name   = name
          ),
          srcPath  = s3TablePath.get,
          columns  = tableColumns.map { x => Column(x.name, TableConverters.apiDataTypeToDomain(x.dataType)) },
          fileType = TableFileType.CSV
        ).map(_ => ())

      case (None, _) => Future.successful(())

      case _ => Future.failed(
        new CortexException("'predictionTable' is not created")
      )
    }
  }

  private def transformProbabilityColumn(probabilityColumn: ProbabilityClassColumn) =
    CortexProbabilityClassColumn(probabilityColumn.className, probabilityColumn.columnName)

  private def toProbabilityPredictionTableSchema(table: PredictionTable) =
    ProbabilityPredictionTableSchema(
      probabilityColumns      = table.probabilityColumns.map(transformProbabilityColumn),
      imageFileNameColumnName = table.filenameColumn,
      areaColumns             = table.areaColumns.map { areaColumns =>
        ProbabilityPredictionAreaColumns(
          xMinColumnName = areaColumns.xMin,
          xMaxColumnName = areaColumns.xMax,
          yMinColumnName = areaColumns.yMin,
          yMaxColumnName = areaColumns.yMax
        )
      }
    )

  private def toColumns(table: PredictionTable) = {
    val areaColumns = table.areaColumns.toSeq.flatMap { areaColumns =>
      Seq(
        TableColumn(areaColumns.xMin, DataType.INTEGER, VariableType.CONTINUOUS),
        TableColumn(areaColumns.yMin, DataType.INTEGER, VariableType.CONTINUOUS),
        TableColumn(areaColumns.xMax, DataType.INTEGER, VariableType.CONTINUOUS),
        TableColumn(areaColumns.yMax, DataType.INTEGER, VariableType.CONTINUOUS)
      )
    }
    val probabilityColumns = table.probabilityColumns.map { column =>
      TableColumn(column.columnName, DataType.DOUBLE, VariableType.CONTINUOUS)
    }
    val columns =
      Seq(
        TableColumn(table.filenameColumn, DataType.STRING, VariableType.CATEGORICAL)
      ) ++
        areaColumns ++
        probabilityColumns :+
        TableColumn(table.referenceIdColumn, DataType.STRING, VariableType.CATEGORICAL)

    columns
  }

  private def fromCortexInputSize(inputSize: InputSize) = {
    LocalizationParams.InputSize(inputSize.width, inputSize.height)
  }

  private def generateOutputTableS3Path(jobId: JobId) = s"$baseTablesPath/$jobId/probabilities.csv"

  override def handlePartial: PartialFunction[(JobId, JobRequest), JobResult] = {
    case (jobId, jobReq) if jobReq.`type` == CVModelTrain =>
      val trainRequest = CVModelTrainRequest.parseFrom(jobReq.payload.toByteArray)
      this.train(jobId, trainRequest)

    case (jobId, jobReq) if jobReq.`type` == CVPredict =>
      val predictRequest = PredictRequest.parseFrom(jobReq.payload.toByteArray)
      this.predict(jobId, predictRequest)

    case (jobId, jobReq) if jobReq.`type` == CVEvaluate =>
      val evaluateRequest = EvaluateRequest.parseFrom(jobReq.payload.toByteArray)
      this.evaluate(jobId, evaluateRequest)

    case (jobId, jobReq) if jobReq.`type` == CVModelImport =>
      val importRequest = CVModelImportRequest.parseFrom(jobReq.payload.toByteArray)
      this.importModel(jobId, importRequest)
  }

  private def getClassReference(modelType: TLModelType) = modelType.`type` match {
    case TLModelType.Type.LocalizerType(localizerClassReference) => localizerClassReference
    case TLModelType.Type.ClassifierType(classifierClassReference) => classifierClassReference
    case TLModelType.Type.AutoencoderType(autoencoderClassReference) => autoencoderClassReference
    case _ => throw new RuntimeException("Unrecognized ModelType")
  }

  private def toParameterValuesMap(map: Map[String, computervision.ParameterValue]) = {
    map.mapValues { v =>
      v.value match {
        case computervision.ParameterValue.Value.StringValue(value)   => StringValue(value)
        case computervision.ParameterValue.Value.IntValue(value)      => IntValue(value)
        case computervision.ParameterValue.Value.FloatValue(value)    => FloatValue(value)
        case computervision.ParameterValue.Value.BooleanValue(value)  => BooleanValue(value)
        case computervision.ParameterValue.Value.StringValues(value)  => StringValues(value.values)
        case computervision.ParameterValue.Value.IntValues(value)     => IntValues(value.values)
        case computervision.ParameterValue.Value.FloatValues(value)   => FloatValues(value.values)
        case computervision.ParameterValue.Value.BooleanValues(value) => BooleanValues(value.values)
        case computervision.ParameterValue.Value.Empty                => throw new RuntimeException("Parameter value cannot be empty")
      }
    }
  }

  private def parseModelType(modelType: TLModelType) = modelType.`type` match {
    case TLModelType.Type.LocalizerType(_)   => "detector"
    case TLModelType.Type.ClassifierType(_)  => "classifier"
    case TLModelType.Type.AutoencoderType(_) => "autoencoder"
    case _                                   => throw new RuntimeException("Unrecognized TLModelType.")
  }

}
// scalastyle:on number.of.methods

object ComputerVisionService {

  def apply(
    scheduler:            TaskScheduler,
    s3AccessParams:       S3AccessParams,
    redshiftAccessParams: RedshiftAccessParams,
    settings:             SettingsModule
  )(implicit executionContext: ExecutionContext): ComputerVisionService = {

    val cvClassificationModule = new ClassificationModule
    val cvClassificationJob = new ClassificationJob(
      scheduler               = scheduler,
      module                  = cvClassificationModule,
      classificationJobConfig = settings.classificationConfig
    )

    val stackedAutoencoderModule = new StackedAutoencoderModule
    val autoencoderJob = new AutoencoderJob(
      scheduler            = scheduler,
      module               = stackedAutoencoderModule,
      autoencoderJobConfig = settings.autoencoderConfig
    )

    val cvLocalizationModule = new LocalizationModule
    val cvLocalizationJob = new LocalizationJob(
      scheduler             = scheduler,
      module                = cvLocalizationModule,
      localizationJobConfig = settings.localizationConfig
    )

    val modelImportModule = new ModelImportModule
    val modelImportJob = new ModelImportJob(
      scheduler            = scheduler,
      module               = modelImportModule,
      modelImportJobConfig = settings.modelImportConfig
    )

    val customModelModule = new CustomModelModule
    val customModelJob = new CustomModelJob(
      scheduler            = scheduler,
      module               = customModelModule,
      customModelJobConfig = settings.customModelJobConfig
    )

    val redshiftExporterModule = new RedshiftExporterModule()
    val redshiftExporterJob = new RedshiftExporterJob(
      scheduler                 = scheduler,
      redshiftAccessParams      = redshiftAccessParams,
      s3AccessParams            = s3AccessParams,
      redshiftExporterModule    = redshiftExporterModule,
      redshiftExporterJobConfig = settings.redshiftExporterConfig
    )

    new ComputerVisionService(
      classificationJob = cvClassificationJob,
      localizationJob   = cvLocalizationJob,
      autoencoderJob    = autoencoderJob,
      modelImportJob    = modelImportJob,
      customModelJob    = customModelJob,
      tableExporterJob  = redshiftExporterJob,
      s3AccessParams    = s3AccessParams,
      modelsBasePath    = settings.modelsPath,
      baseTablesPath    = settings.jobsPath //TODO: replace it by `baseS3Config.tablesDir` after COR-2031
    )
  }
}
