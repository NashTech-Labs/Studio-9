package baile.services.cv.model

import java.util.UUID

import baile.dao.cv.model.CVModelDao
import baile.dao.experiment.ExperimentDao
import baile.daocommons.WithId
import baile.domain.asset.AssetType
import baile.domain.common.CortexModelReference
import baile.domain.pipeline.PipelineParams._
import baile.domain.cv.{ CommonTrainParams, LabelOfInterest }
import baile.domain.cv.CommonTrainParams.InputSize
import baile.domain.cv.model._
import baile.domain.cv.model.tlprimitives.CVTLModelPrimitive
import baile.domain.cv.result.{ CVTLTrainResult, CVTLTrainStepResult }
import baile.domain.experiment.{ Experiment, ExperimentStatus }
import baile.domain.images.augmentation._
import baile.domain.job.{ CortexJobStatus, CortexJobTerminalStatus }
import baile.domain.table.Table
import baile.services.cortex.job.{ CortexJobService, JobMetaService }
import baile.services.cv.CVTLModelPrimitiveService
import baile.services.cv.model.CVModelTrainPipelineHandler.{ NonSuccessfulTerminalStatus, SpecifiedCVPipelineParam }
import baile.services.cv.model.CVModelTrainResultHandler.{ Meta, NextStepParams }
import baile.services.dcproject.DCProjectPackageService
import baile.services.experiment.ExperimentCommonService
import baile.services.images.ImagesCommonService
import baile.services.images.ImagesCommonService.AugmentationResultImage
import baile.services.process.{ JobResultHandler, ProcessService }
import baile.services.table.TableService
import baile.utils.TryExtensions._
import baile.utils.json.{ CommonFormats, EnumFormatBuilder }
import cortex.api.job.common.ClassReference
import cats.data.OptionT
import cats.implicits._
import cortex.api.job.computervision.{ CVModelTrainRequest, CVModelTrainResult }
import play.api.libs.json._

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

class CVModelTrainResultHandler(
  modelDao: CVModelDao,
  experimentDao: ExperimentDao,
  experimentCommonService: ExperimentCommonService,
  cvModelTrainPipelineHandler: CVModelTrainPipelineHandler,
  cvModelPrimitiveService: CVTLModelPrimitiveService,
  packageService: DCProjectPackageService,
  processService: ProcessService,
  imagesCommonService: ImagesCommonService,
  cvModelCommonService: CVModelCommonService,
  cortexJobService: CortexJobService,
  jobMetaService: JobMetaService,
  tableService: TableService
) extends JobResultHandler[Meta] {

  override protected val metaReads: Reads[Meta] = CVModelTrainResultHandler.CVModelTrainResultHandlerMetaFormat

  // scalastyle:off method.length
  override protected def handleResult(
    jobId: UUID,
    lastStatus: CortexJobTerminalStatus,
    meta: Meta
  )(implicit ec: ExecutionContext): Future[Unit] =
    for {
      model <- cvModelCommonService.loadModelMandatory(meta.modelId)
      _ <- cvModelCommonService.assertModelStatus(model, CVModelStatus.Training).toFuture
      _ <- lastStatus match {
        case CortexJobStatus.Completed =>
          for {
            experiment <- experimentCommonService.loadExperimentMandatory(meta.experimentId)
            outputPath <- cortexJobService.getJobOutputPath(jobId)
            jobTimeSummary <- cortexJobService.getJobTimeSummary(jobId)
            rawJobResult <- jobMetaService.readRawMeta(jobId, outputPath)
            trainResult <- Try(CVModelTrainResult.parseFrom(rawJobResult)).toFuture
            _ <- modelDao.update(
              model.id,
              _.copy(
                cortexModelReference = trainResult.cvModelReference.map { modelReference =>
                  CortexModelReference(
                    cortexId = modelReference.id,
                    cortexFilePath = modelReference.filePath
                  )
                },
                cortexFeatureExtractorReference = trainResult.featureExtractorReference.map { feReference =>
                  CortexModelReference(
                    cortexId = feReference.id,
                    cortexFilePath = feReference.filePath
                  )
                },
                classNames = trainResult.confusionMatrix.map(_.labels),
                experimentId = Some(meta.experimentId)
              )
            ).map(_.getOrElse(throw new RuntimeException(s"Failed to update model ${ model.id }")))
            augmentationSummary <- cvModelCommonService.buildAugmentationSummary(
              trainResult.augmentationSummary
            ).toFuture
            stepResult = CVTLTrainStepResult(
              modelId = model.id,
              outputAlbumId = meta.outputAlbumId,
              testOutputAlbumId = meta.testOutputAlbumId,
              autoAugmentationSampleAlbumId = meta.autoDASampleAlbumId,
              probabilityPredictionTableId = meta.probabilityPredictionTableId,
              testProbabilityPredictionTableId = meta.testProbabilityPredictionTableId,
              summary = Some(CVModelSummary(
                labels = trainResult.confusionMatrix.fold[Seq[String]](Seq.empty)(_.labels),
                confusionMatrix = trainResult.confusionMatrix.map(_.confusionMatrixCells.map(
                  CVModelCommonService.buildConfusionMatrixCell
                )),
                mAP = trainResult.map,
                reconstructionLoss = trainResult.reconstructionLoss
              )),
              testSummary = None,
              augmentationSummary = augmentationSummary,
              trainTimeSpentSummary = Some(CVModelTrainTimeSpentSummary(
                dataFetchTime = trainResult.dataFetchTime,
                trainingTime = trainResult.trainingTime,
                saveModelTime = trainResult.saveModelTime,
                predictionTime = trainResult.predictionTime,
                tasksQueuedTime = jobTimeSummary.tasksQueuedTime,
                totalJobTime = jobTimeSummary.calculateTotalJobTime,
                pipelineTimings = cortexJobService.buildPipelineTimings(trainResult.pipelineTimings)
              )),
              evaluateTimeSpentSummary = None
            )
            oldExperimentResult <- experimentCommonService.getExperimentResultAs[CVTLTrainResult](
              experiment.entity
            ).toFuture
            experimentResult = oldExperimentResult match {
              case Some(result) => result.copy(stepTwo = Some(stepResult))
              case None => CVTLTrainResult(stepOne = stepResult, stepTwo = None)
            }
            _ <- cvModelCommonService.populateOutputAlbumIfNeeded(
              inputAlbumId = meta.inputAlbumId,
              outputAlbumId = stepResult.outputAlbumId,
              predictedImages = trainResult.images
            )
            _ <- cvModelCommonService.populateSampleDAAlbum(
              inputAlbumId = meta.inputAlbumId,
              sampleDAAlbumId = meta.autoDASampleAlbumId,
              augmentedImages = trainResult.augmentedImages.map { augmentedImage =>
                AugmentationResultImage(
                  augmentedImage.getImage,
                  Some(augmentedImage.fileSize),
                  augmentedImage.augmentations
                )
              }
            )
            _ <- cvModelCommonService.updatePredictionTableColumnsAndCalculateStatistics(
              probabilityPredictionTableId = meta.probabilityPredictionTableId,
              probabilityPredictionTableSchema = trainResult.probabilityPredictionTableSchema,
              modelType = model.entity.`type`,
              userId = meta.userId
            )
            _ <- experimentDao.update(experiment.id, _.copy(result = Some(experimentResult)))
            thisStepEvaluationMeta = meta.testInputAlbumId.map { testInputAlbumId =>
              CVModelEvaluateResultHandler.StepMeta(
                modelId = model.id,
                experimentId = experiment.id,
                testInputAlbumId = testInputAlbumId,
                userId = meta.userId,
                probabilityPredictionTableId = meta.testProbabilityPredictionTableId
              )
            }
            newEvaluationsMeta = meta.evaluationsMeta ++ thisStepEvaluationMeta
            _ <- meta.nextStepParams match {
              case Some(params) =>
                launchNextStep(experiment, meta.copy(evaluationsMeta = newEvaluationsMeta), params)
              case None =>
                startEvaluations(experiment.id, experimentResult, newEvaluationsMeta)
            }
          } yield ()
        case CortexJobStatus.Cancelled =>
          for {
            experiment <- experimentDao.get(meta.experimentId)
            _ <- experiment match {
              case Some(experiment) => finishExperimentNonSuccessfully(experiment, meta, ExperimentStatus.Cancelled)
              case None => Future.unit
            }
          } yield ()
        case CortexJobStatus.Failed =>
          for {
            experiment <- experimentDao.get(meta.experimentId)
            _ <- experiment match {
              case Some(experiment) => finishExperimentNonSuccessfully(experiment, meta, ExperimentStatus.Error)
              case None => Future.unit
            }
          } yield ()
      }
    } yield ()

  // scalastyle:on method.length
  override protected def handleException(meta: Meta): Future[Unit] =
    for {
      experiment <- experimentCommonService.loadExperimentMandatory(meta.experimentId)
      _ <- finishExperimentNonSuccessfully(experiment, meta, ExperimentStatus.Error)
    } yield ()

  // scalastyle:off method.length
  private def launchNextStep(experiment: WithId[Experiment], meta: Meta, nextStepParams: NextStepParams): Future[Unit] =
    for {
      modelName <- cvModelTrainPipelineHandler.generateNewName(experiment.entity.name + " Model", meta.userId)
      inputAlbum <- imagesCommonService.getAlbumMandatory(nextStepParams.inputAlbumId)
      testInputAlbum <- nextStepParams.testInputAlbumId match {
        case Some(id) => imagesCommonService.getAlbumMandatory(id).map(Some(_))
        case None => Future.successful(None)
      }
      outputAlbum <- cvModelTrainPipelineHandler.createOutputAlbumIfNeeded(
        inputAlbum = inputAlbum.entity,
        modelType = nextStepParams.modelType,
        modelName = modelName,
        userId = meta.userId
      )
      autoDASampleAlbum <- if (nextStepParams.autoAugmentationParams.fold(false)(_.generateSampleAlbum)) {
        cvModelCommonService.createAutoDASampleAlbum(
          inputAlbum = inputAlbum.entity,
          userId = meta.userId
        ).map(Some(_))
      } else {
        Future.successful(None)
      }
      testOutputAlbum <- testInputAlbum match {
        case Some(album) => cvModelTrainPipelineHandler.createOutputAlbumIfNeeded(
          inputAlbum = album.entity,
          modelType = nextStepParams.modelType,
          modelName = modelName,
          userId = meta.userId
        )
        case None => Future.successful(None)
      }
      featureExtractor <- cvModelCommonService.loadModelMandatory(nextStepParams.featureExtractorId)
      feType = cvModelTrainPipelineHandler.toTlModelType(featureExtractor.entity.`type`).getOrElse(
        throw new RuntimeException(
          s"Invalid feature extractor type: ${ featureExtractor.entity.`type` }; it must be TL"
        )
      )
      model <- cvModelTrainPipelineHandler.createModel(
        name = modelName,
        status = CVModelStatus.Training,
        description = experiment.entity.description,
        modelType = nextStepParams.modelType,
        featureExtractorId = Some(nextStepParams.featureExtractorId),
        featureExtractorArchitecture = feType.featureExtractorArchitecture,
        userId = meta.userId,
        experimentId = experiment.id
      )
      feCortexId <- cvModelCommonService.getCortexFeatureExtractorId(featureExtractor).toFuture
      architectureOperator <- cvModelPrimitiveService.loadFeatureExtractorArchitecturePrimitive(
        feType.featureExtractorArchitecture
      )
      architectureOperatorPackage <- packageService.loadPackageMandatory(architectureOperator.entity.packageId)
      modelOperator <- cvModelPrimitiveService.loadTLConsumerPrimitive(nextStepParams.modelType)
      modelOperatorPackage <- packageService.loadPackageMandatory(modelOperator.entity.packageId)
      modelCVPipelineParams <- buildPipelineParams(
        model.id,
        nextStepParams.modelParams,
        modelOperator.entity
      ).toFuture
      inputPictures <- imagesCommonService.getPictures(inputAlbum.id, onlyTagged = false)
      probabilityPredictionTable <- loadOptionalPredictionTable(nextStepParams.probabilityPredictionTableId)
      testProbabilityPredictionTable <- loadOptionalPredictionTable(nextStepParams.testProbabilityPredictionTableId)
      baseJobMessage = CVModelTrainRequest(
        featureExtractorId = Some(feCortexId),
        featureExtractorClassReference = Some(ClassReference(
          packageLocation = architectureOperatorPackage.entity.location,
          className = architectureOperator.entity.className,
          moduleName = architectureOperator.entity.moduleName
        )),
        images = imagesCommonService.convertPicturesToCortexTaggedImages(inputPictures),
        filePathPrefix = imagesCommonService.getImagesPathPrefix(inputAlbum.entity),
        modelType = Some(cvModelCommonService.buildCortexTLConsumer(
          nextStepParams.modelType,
          ClassReference(
            packageLocation = modelOperatorPackage.entity.location,
            className = modelOperator.entity.className,
            moduleName = modelOperator.entity.moduleName
          )
        )),
        augmentationParams = nextStepParams.autoAugmentationParams.map { autoDAParams =>
          cvModelCommonService.buildCortexAutoAugmentationParams(
            params = autoDAParams,
            targetPrefix = autoDASampleAlbum.map(album => imagesCommonService.getImagesPathPrefix(album.entity))
          )
        },
        modelParameters = modelCVPipelineParams.mapValues { param =>
          cvModelTrainPipelineHandler.buildParameterValue(param.value, param.definition.typeInfo)
        },
        tuneFeatureExtractor = nextStepParams.tuneFeatureExtractor,
        probabilityPredictionTable = probabilityPredictionTable.map { table =>
          tableService.buildTableMeta(table.entity)
        }
      )
      jobMessage = CVModelCommonService.addTrainParamsToRequest(
        CVModelTrainRequest(
          featureExtractorId = Some(feCortexId),
          featureExtractorClassReference = Some(ClassReference(
            packageLocation = architectureOperatorPackage.entity.location,
            className = architectureOperator.entity.className,
            moduleName = architectureOperator.entity.moduleName
          )),
          images = imagesCommonService.convertPicturesToCortexTaggedImages(inputPictures),
          filePathPrefix = imagesCommonService.getImagesPathPrefix(inputAlbum.entity),
          modelType = Some(cvModelCommonService.buildCortexTLConsumer(
            nextStepParams.modelType,
            ClassReference(
              packageLocation = modelOperatorPackage.entity.location,
              className = modelOperator.entity.className,
              moduleName = modelOperator.entity.moduleName
            )
          )),
          augmentationParams = nextStepParams.autoAugmentationParams.map { autoDAParams =>
            cvModelCommonService.buildCortexAutoAugmentationParams(
              params = autoDAParams,
              targetPrefix = autoDASampleAlbum.map(album => imagesCommonService.getImagesPathPrefix(album.entity))
            )
          },
          modelParameters = modelCVPipelineParams.mapValues { param =>
            cvModelTrainPipelineHandler.buildParameterValue(param.value, param.definition.typeInfo)
          },
          tuneFeatureExtractor = nextStepParams.tuneFeatureExtractor,
          probabilityPredictionTable = probabilityPredictionTable.map { table =>
            tableService.buildTableMeta(table.entity)
          }
        ),
        nextStepParams.trainParams
      )
      trainJobId <- cortexJobService.submitJob(jobMessage, meta.userId)
      _ <- processService.startProcess(
        jobId = trainJobId,
        targetId = experiment.id,
        targetType = AssetType.Experiment,
        handlerClass = classOf[CVModelTrainResultHandler],
        meta = CVModelTrainResultHandler.Meta(
          modelId = model.id,
          inputAlbumId = inputAlbum.id,
          testInputAlbumId = testInputAlbum.map(_.id),
          userId = meta.userId,
          experimentId = experiment.id,
          outputAlbumId = outputAlbum.map(_.id),
          testOutputAlbumId = testOutputAlbum.map(_.id),
          autoDASampleAlbumId = autoDASampleAlbum.map(_.id),
          probabilityPredictionTableId = probabilityPredictionTable.map(_.id),
          testProbabilityPredictionTableId = testProbabilityPredictionTable.map(_.id),
          nextStepParams = None,
          evaluationsMeta = meta.evaluationsMeta
        ),
        userId = meta.userId
      )
    } yield ()
  // scalastyle:on method.length

  def buildPipelineParams(
    modelId: String,
    params: PipelineParams,
    cvTLModelPrimitive: CVTLModelPrimitive
  ): Try[Map[String, SpecifiedCVPipelineParam]] = Try {
    params.foldLeft[Map[String, SpecifiedCVPipelineParam]](Map.empty) { case (soFar, (name, param)) =>
      val operatorParam = cvTLModelPrimitive.params.find(_.name == name).getOrElse(
        throw new RuntimeException(s"Could not find param $name for model $modelId")
      )
      soFar + (name -> SpecifiedCVPipelineParam(
        value = param,
        definition = operatorParam
      ))
    }
  }

  private def startEvaluations(
    experimentId: String,
    experimentResult: CVTLTrainResult,
    evaluationsMeta: List[CVModelEvaluateResultHandler.StepMeta]
  ): Future[Unit] =
    evaluationsMeta match {
      case Nil =>
        for {
          _ <- experimentDao.update(experimentId, _.copy(status = ExperimentStatus.Completed))
          _ <- cvModelTrainPipelineHandler.updateOutputEntitiesOnSuccess(experimentResult)
        } yield ()
      case evaluationMeta :: nextSteps =>
        for {
          _ <- cvModelCommonService.updateModelStatus(evaluationMeta.modelId, CVModelStatus.Predicting)
          _ <- cvModelTrainPipelineHandler.launchEvaluation(evaluationMeta, nextSteps)
        } yield ()
    }

  private def finishExperimentNonSuccessfully[S <: ExperimentStatus: NonSuccessfulTerminalStatus](
    experiment: WithId[Experiment],
    meta: Meta,
    status: S
  ): Future[Unit] = {
    val cvTrainStepResult = CVTLTrainStepResult(
      modelId = meta.modelId,
      outputAlbumId = meta.outputAlbumId,
      testOutputAlbumId = meta.testOutputAlbumId,
      autoAugmentationSampleAlbumId = meta.autoDASampleAlbumId,
      probabilityPredictionTableId = meta.probabilityPredictionTableId,
      testProbabilityPredictionTableId = meta.testProbabilityPredictionTableId,
      summary = None,
      testSummary = None,
      augmentationSummary = None,
      trainTimeSpentSummary = None,
      evaluateTimeSpentSummary = None
    )
    for {
      currentExperimentResult <- experimentCommonService.getExperimentResultAs[CVTLTrainResult](
        experiment.entity
      ).toFuture
      newExperimentResult = currentExperimentResult match {
        case Some(result) => result.copy(stepTwo = Some(cvTrainStepResult))
        case None => CVTLTrainResult(stepOne = cvTrainStepResult, stepTwo = None)
      }
      _ <- cvModelTrainPipelineHandler.updateOutputEntitiesOnNoSuccess(
        result = newExperimentResult,
        status = status
      )
      _ <- experimentDao.update(
        experiment.id,
        _.copy(status = status, result = Some(newExperimentResult))
      )
    } yield ()
  }

  private def loadOptionalPredictionTable(tableId: Option[String]): Future[Option[WithId[Table]]] =
    OptionT.fromOption[Future](tableId)
      .semiflatMap(tableService.loadTableMandatory)
      .value

}

object CVModelTrainResultHandler {

  case class Meta(
    modelId: String,
    inputAlbumId: String,
    testInputAlbumId: Option[String],
    userId: UUID,
    experimentId: String,
    outputAlbumId: Option[String],
    testOutputAlbumId: Option[String],
    autoDASampleAlbumId: Option[String],
    probabilityPredictionTableId: Option[String],
    testProbabilityPredictionTableId: Option[String],
    nextStepParams: Option[NextStepParams],
    evaluationsMeta: List[CVModelEvaluateResultHandler.StepMeta]
  )

  case class NextStepParams(
    featureExtractorId: String,
    inputAlbumId: String,
    tuneFeatureExtractor: Boolean,
    autoAugmentationParams: Option[AutomatedAugmentationParams],
    testInputAlbumId: Option[String],
    modelParams: PipelineParams,
    modelType: CVModelType.TLConsumer,
    probabilityPredictionTableId: Option[String],
    testProbabilityPredictionTableId: Option[String],
    trainParams: Option[CommonTrainParams]
  )

  private implicit val BlurringParamsFormat: OFormat[BlurringParams] = Json.format[BlurringParams]

  private implicit val CroppingParamsFormat: OFormat[CroppingParams] = Json.format[CroppingParams]

  private implicit val MirroringAxisToFlipFormat: Format[MirroringAxisToFlip] = EnumFormatBuilder.build(
  {
    case "BOTH" => MirroringAxisToFlip.Both
    case "HORIZONTAL" => MirroringAxisToFlip.Horizontal
    case "VERTICAL" => MirroringAxisToFlip.Vertical
  },
  {
    case MirroringAxisToFlip.Both => "BOTH"
    case MirroringAxisToFlip.Horizontal => "HORIZONTAL"
    case MirroringAxisToFlip.Vertical => "VERTICAL"
  },
  "Mirroring axis to flip"
  )
  private implicit val MirroringParamsFormat: OFormat[MirroringParams] = Json.format[MirroringParams]

  private implicit val NoisingParamsFormat: OFormat[NoisingParams] = Json.format[NoisingParams]

  private implicit val OcclusionModeFormat: Format[OcclusionMode] = EnumFormatBuilder.build(
  {
    case "BACKGROUND" => OcclusionMode.Background
    case "ZERO" => OcclusionMode.Zero
  },
  {
    case OcclusionMode.Background => "BACKGROUND"
    case OcclusionMode.Zero => "ZERO"
  },
  "Occlusion mode"
  )
  private implicit val OcclusionParamsFormat: OFormat[OcclusionParams] = Json.format[OcclusionParams]

  private implicit val PhotometricDistortAlphaBoundsFormat: OFormat[PhotometricDistortAlphaBounds] =
    Json.format[PhotometricDistortAlphaBounds]
  private implicit val PhotometricDistortParamsFormat: OFormat[PhotometricDistortParams] =
    Json.format[PhotometricDistortParams]

  private implicit val RotationParamsFormat: OFormat[RotationParams] = Json.format[RotationParams]

  private implicit val SaltPepperParamsFormat: OFormat[SaltPepperParams] = Json.format[SaltPepperParams]

  private implicit val ShearingParamsFormat: OFormat[ShearingParams] = Json.format[ShearingParams]

  private implicit val TranslationModeFormat: Format[TranslationMode] = EnumFormatBuilder.build(
  {
    case "REFLECT" => TranslationMode.Reflect
    case "CONSTANT" => TranslationMode.Constant
  },
  {
    case TranslationMode.Reflect => "REFLECT"
    case TranslationMode.Constant => "CONSTANT"
  },
  "Translation mode"
  )
  private implicit val TranslationParamsFormat: OFormat[TranslationParams] = Json.format[TranslationParams]

  private implicit val ZoomInParamsFormat: OFormat[ZoomInParams] = Json.format[ZoomInParams]

  private implicit val ZoomOutParamsFormat: OFormat[ZoomOutParams] = Json.format[ZoomOutParams]

  private implicit val AugmentationParamsFormat: OFormat[AugmentationParams] = OFormat.apply(
    r = for {
      typeName <- (__ \ "augmentationType").read[String]
      result <- typeName match {
        case "BlurringParams" => BlurringParamsFormat
        case "CroppingParams" => CroppingParamsFormat
        case "MirroringParams" => MirroringParamsFormat
        case "NoisingParams" => NoisingParamsFormat
        case "OcclusionParams" => OcclusionParamsFormat
        case "PhotometricDistortParams" => PhotometricDistortParamsFormat
        case "RotationParams" => RotationParamsFormat
        case "SaltPepperParams" => SaltPepperParamsFormat
        case "ShearingParams" => ShearingParamsFormat
        case "TranslationParams" => TranslationParamsFormat
        case "ZoomInParams" => ZoomInParamsFormat
        case "ZoomOutParams" => ZoomOutParamsFormat
        case unknown => Reads(_ => JsError(s"Unknown augmentations params type $unknown"))
      }
    } yield result,
    w =  {

      def buildResult[T <: AugmentationParams](params: T, typeName: String, writes: OWrites[T]): JsObject =
        Json.toJsObject(params)(writes) + ("augmentationType" -> JsString(typeName))

      OWrites[AugmentationParams] {
        case params: BlurringParams =>
          buildResult[BlurringParams](params, "BlurringParams", BlurringParamsFormat)
        case params: CroppingParams =>
          buildResult[CroppingParams](params, "CroppingParams", CroppingParamsFormat)
        case params: MirroringParams =>
          buildResult[MirroringParams](params, "MirroringParams", MirroringParamsFormat)
        case params: NoisingParams =>
          buildResult[NoisingParams](params, "NoisingParams", NoisingParamsFormat)
        case params: OcclusionParams =>
          buildResult[OcclusionParams](params, "OcclusionParams", OcclusionParamsFormat)
        case params: PhotometricDistortParams =>
          buildResult[PhotometricDistortParams](params, "PhotometricDistortParams", PhotometricDistortParamsFormat)
        case params: RotationParams =>
          buildResult[RotationParams](params, "RotationParams", RotationParamsFormat)
        case params: SaltPepperParams =>
          buildResult[SaltPepperParams](params, "SaltPepperParams", SaltPepperParamsFormat)
        case params: ShearingParams =>
          buildResult[ShearingParams](params, "ShearingParams", ShearingParamsFormat)
        case params: TranslationParams =>
          buildResult[TranslationParams](params, "TranslationParams", TranslationParamsFormat)
        case params: ZoomInParams =>
          buildResult[ZoomInParams](params, "ZoomInParams", ZoomInParamsFormat)
        case params: ZoomOutParams =>
          buildResult[ZoomOutParams](params, "ZoomOutParams", ZoomOutParamsFormat)
        case unknown => throw new RuntimeException(
          s"Can not serialize type ${ unknown.getClass.getCanonicalName } to json"
        )
      }
    }
  )

  private implicit val AutomatedAugmentationParamsFormat: OFormat[AutomatedAugmentationParams] =
    Json.format[AutomatedAugmentationParams]

  private implicit val CVPipelineParamFormat: Format[PipelineParam] = new Format[PipelineParam] {
    override def reads(json: JsValue): JsResult[PipelineParam] = json match {
      case JsString(value) => JsSuccess(StringParam(value))
      case JsNumber(value) if value.isValidInt => JsSuccess(IntParam(value.toInt))
      case JsNumber(value) if value.isDecimalFloat => JsSuccess(FloatParam(value.toFloat))
      case JsBoolean(value) => JsSuccess(BooleanParam(value))
      case JsArray(values) =>
        if (values.isEmpty) {
          JsSuccess(EmptySeqParam)
        } else if (values.forall(_.validate[String].isSuccess)) {
          JsSuccess(StringParams(values.map(_.as[String])))
        } else if (values.forall(_.validate[Int].isSuccess)) {
          JsSuccess(IntParams(values.map(_.as[Int])))
        } else if (values.forall(_.validate[Float].isSuccess)) {
          JsSuccess(FloatParams(values.map(_.as[Float])))
        } else if (values.forall(_.validate[Boolean].isSuccess)) {
          JsSuccess(BooleanParams(values.map(_.as[Boolean])))
        } else JsError("Expected json string|number[int or float]|bool for multiple cortex pipeline parameter")
      case _ => JsError(
        "Expected json string|number[int or float]|bool or sequence of one of these types for cortex pipeline parameter"
      )
    }

    override def writes(o: PipelineParam): JsValue = o match {
      case StringParam(value) => JsString(value)
      case IntParam(value) => JsNumber(value)
      case FloatParam(value) => CommonFormats.FloatWrites.writes(value)
      case BooleanParam(value) => JsBoolean(value)
      case StringParams(values) => JsArray(values.map(JsString))
      case IntParams(values) => JsArray(values.map(value => JsNumber(value)))
      case FloatParams(values) => JsArray(values.map(value => CommonFormats.FloatWrites.writes(value)))
      case BooleanParams(values) => JsArray(values.map(JsBoolean))
      case EmptySeqParam => JsArray.empty
    }
  }

  private implicit val CVModelTypeFormat: Format[CVModelType.TLConsumer] = Format[CVModelType.TLConsumer](
    for {
      typeName <- (__ \ "__type").read[String]
      operatorId <- (__ \ "operatorId").read[String]
      modelTypeReference <- typeName match {
        case "CLASSIFIER" => Reads.pure(CVModelType.TLConsumer.Classifier(operatorId))
        case "LOCALIZER" => Reads.pure(CVModelType.TLConsumer.Localizer(operatorId))
        case "DECODER" => Reads.pure(CVModelType.TLConsumer.Decoder(operatorId))
        case unknown => Reads(_ => JsError(s"Unknown model type: $unknown"))
      }
    } yield modelTypeReference,

    Writes[CVModelType.TLConsumer] { modelType =>
      val typeName = modelType match {
        case _: CVModelType.TLConsumer.Classifier => "CLASSIFIER"
        case _: CVModelType.TLConsumer.Localizer => "LOCALIZER"
        case _: CVModelType.TLConsumer.Decoder => "DECODER"
      }
      Json.obj(
        "operatorId" -> modelType.operatorId,
        "__type" -> typeName
      )
    }
  )

  private implicit val InputSizeFormat: OFormat[InputSize] = Json.format[InputSize]

  private implicit val LabelOfInterestFormat: OFormat[LabelOfInterest] = Json.format[LabelOfInterest]

  private implicit val CommonTrainParamsFormat: OFormat[CommonTrainParams] = Json.format[CommonTrainParams]

  implicit val CVModelTrainResultHandlerNextStepParamsFormat: OFormat[NextStepParams] = Json.format[NextStepParams]

  implicit val CVModelTrainResultHandlerMetaFormat: OFormat[Meta] = Json.format[Meta]

}
