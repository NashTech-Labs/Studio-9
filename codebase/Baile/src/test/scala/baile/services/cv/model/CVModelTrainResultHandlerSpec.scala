package baile.services.cv.model

import java.time.Instant
import java.util.UUID

import akka.actor.Props
import akka.pattern.ask
import baile.RandomGenerators.randomString
import baile.dao.cv.model.CVModelDao
import baile.dao.experiment.ExperimentDao
import baile.daocommons.WithId
import baile.domain.asset.AssetType
import baile.domain.common.{ ConfusionMatrixCell, Version }
import baile.domain.pipeline.PipelineParams.{ BooleanParam, FloatParam, IntParam, StringParam }
import baile.domain.cv.{ CommonTrainParams, LabelOfInterest }
import baile.domain.cv.CommonTrainParams.InputSize
import baile.domain.cv.model._
import baile.domain.cv.model.tlprimitives.{ CVTLModelPrimitive, CVTLModelPrimitiveType }
import baile.domain.cv.pipeline.FeatureExtractorParams.CreateNewFeatureExtractorParams
import baile.domain.cv.pipeline.{ CVTLTrainPipeline, CVTLTrainStep1Params }
import baile.domain.cv.result.{ CVTLTrainResult, CVTLTrainStepResult }
import baile.domain.dcproject.DCProjectPackage
import baile.domain.experiment.ExperimentStatus
import baile.domain.images.augmentation._
import baile.domain.images.{ AlbumLabelMode, AlbumStatus }
import baile.domain.job.{ CortexJobStatus, CortexJobTimeSpentSummary, CortexTimeInfo }
import baile.services.cortex.job.{ CortexJobService, JobMetaService }
import baile.services.cv.CVTLModelPrimitiveService
import baile.services.cv.model.CVModelTrainResultHandler.NextStepParams
import baile.services.dcproject.DCProjectPackageService
import baile.services.experiment.{ ExperimentCommonService, ExperimentRandomGenerator }
import baile.services.images.ImagesCommonService
import baile.services.images.util.ImagesRandomGenerator.randomAlbum
import baile.services.process.JobResultHandler.{ HandleException, HandleJobResult }
import baile.services.process.ProcessService
import baile.services.process.util.ProcessRandomGenerator
import baile.services.table.util.TableRandomGenerator
import baile.services.table.TableService
import baile.services.usermanagement.util.TestData
import baile.{ ExtendedBaseSpec, RandomGenerators }
import cortex.api.job.common.ModelReference
import cortex.api.job.album.common.Image
import cortex.api.job.computervision.{ CVModelTrainRequest, CVModelTrainResult, PredictedImage, TLModelType }
import cortex.api.job.common.{ ConfusionMatrix => CortexConfusionMatrix, ConfusionMatrixCell => CortexConfusionMatrixCell, _ }
import play.api.libs.json.Json

import scala.util.Try

class CVModelTrainResultHandlerSpec extends ExtendedBaseSpec {

  trait Setup {

    val modelDao = mock[CVModelDao]
    val experimentDao = mock[ExperimentDao]
    val experimentCommonService = mock[ExperimentCommonService]
    val cvModelTrainPipelineHandler = mock[CVModelTrainPipelineHandler]
    val cvModelPrimitiveService = mock[CVTLModelPrimitiveService]
    val packageService = mock[DCProjectPackageService]
    val processService = mock[ProcessService]
    val imagesCommonService = mock[ImagesCommonService]
    val cvModelCommonService = mock[CVModelCommonService]
    val cortexJobService = mock[CortexJobService]
    val jobMetaService = mock[JobMetaService]
    val tableService = mock[TableService]

    val handler = system.actorOf(Props(new CVModelTrainResultHandler(
      modelDao = modelDao,
      experimentDao = experimentDao,
      experimentCommonService = experimentCommonService,
      cvModelTrainPipelineHandler = cvModelTrainPipelineHandler,
      cvModelPrimitiveService = cvModelPrimitiveService,
      packageService = packageService,
      processService = processService,
      imagesCommonService = imagesCommonService,
      cvModelCommonService = cvModelCommonService,
      cortexJobService = cortexJobService,
      jobMetaService = jobMetaService,
      tableService = tableService
    )))

    val user = TestData.SampleUser
    val jobId = UUID.randomUUID()
    val modelType = CVModelRandomGenerator.randomTLModelType()
    val model = CVModelRandomGenerator.randomModel(
      status = CVModelStatus.Training,
      modelType = modelType
    )
    val outputPath = RandomGenerators.randomString()
    val inputAlbum = randomAlbum(
      status = AlbumStatus.Active,
      labelMode = AlbumLabelMode.Classification
    )
    val testInputAlbum = randomAlbum(
      status = AlbumStatus.Active,
      labelMode = AlbumLabelMode.Classification
    )
    val outputAlbum = randomAlbum(
      status = AlbumStatus.Active,
      labelMode = AlbumLabelMode.Classification
    )
    val testOutputAlbum = randomAlbum(
      status = AlbumStatus.Active,
      labelMode = AlbumLabelMode.Classification
    )
    val autoDASampleAlbum = randomAlbum(
      status = AlbumStatus.Active,
      labelMode = AlbumLabelMode.Classification
    )
    val experiment = ExperimentRandomGenerator.randomExperiment(
      pipeline = CVTLTrainPipeline(
        CVTLTrainStep1Params(
          feParams = CreateNewFeatureExtractorParams(randomString(), Map.empty),
          modelType = modelType.consumer,
          modelParams = Map.empty,
          inputAlbumId = inputAlbum.id,
          testInputAlbumId = Some(testInputAlbum.id),
          automatedAugmentationParams = None,
          trainParams = None
        ),
        None
      )
    )

    val labels = Seq("label1", "label2")
    val cortexConfusionMatrix = CortexConfusionMatrix(
      confusionMatrixCells = Seq(
        CortexConfusionMatrixCell(Some(0), Some(0), 1),
        CortexConfusionMatrixCell(Some(1), Some(0), 0),
        CortexConfusionMatrixCell(Some(0), Some(1), 1),
        CortexConfusionMatrixCell(Some(1), Some(1), 1)
      ),
      labels = labels
    )
    val confusionMatrix = Seq(
      ConfusionMatrixCell(Some(0), Some(0), 1),
      ConfusionMatrixCell(Some(1), Some(0), 0),
      ConfusionMatrixCell(Some(0), Some(1), 1),
      ConfusionMatrixCell(Some(1), Some(1), 1)
    )
    val jobResult = CVModelTrainResult(
      featureExtractorReference = Some(ModelReference("feid", "fepath")),
      cvModelReference = Some(ModelReference("modelid", "modelfilepath")),
      images = Seq(
        PredictedImage(Some(Image("img1.png", Some("i1"), Some(12L)))),
        PredictedImage(Some(Image("img2.png", Some("i2"), Some(2L)))),
        PredictedImage(Some(Image("img3.png", Some("i3"), Some(3L))))
      ),
      confusionMatrix = Some(cortexConfusionMatrix),
      map = Some(0.12),
      augmentedImages = Seq.empty,
      augmentationSummary = None,
      dataFetchTime = 100,
      trainingTime = 1123,
      saveModelTime = 94,
      predictionTime = 119,
      reconstructionLoss = Some(0.001)
    )

    val jobTimeSummary = CortexJobTimeSpentSummary(
      tasksQueuedTime = 10L,
      jobTimeInfo = CortexTimeInfo(Instant.now(), Instant.now(), Instant.now()),
      Seq.empty
    )
    val trainStepResult = CVTLTrainStepResult(
      modelId = model.id,
      outputAlbumId = Some(outputAlbum.id),
      testOutputAlbumId = Some(testOutputAlbum.id),
      autoAugmentationSampleAlbumId = Some(autoDASampleAlbum.id),
      summary = None,
      testSummary = None,
      augmentationSummary = None,
      trainTimeSpentSummary = None,
      evaluateTimeSpentSummary = None,
      probabilityPredictionTableId = None,
      testProbabilityPredictionTableId = None
    )
    val trainResult = CVTLTrainResult(trainStepResult, None)
    val updatedTrainStepResult = trainStepResult.copy(
      summary = Some(CVModelSummary(
        labels = labels,
        confusionMatrix = Some(confusionMatrix),
        mAP = jobResult.map,
        reconstructionLoss = jobResult.reconstructionLoss
      )),
      augmentationSummary = None,
      trainTimeSpentSummary = Some(CVModelTrainTimeSpentSummary(
        dataFetchTime = jobResult.dataFetchTime,
        trainingTime = jobResult.trainingTime,
        saveModelTime = jobResult.saveModelTime,
        predictionTime = jobResult.predictionTime,
        tasksQueuedTime = jobTimeSummary.tasksQueuedTime,
        totalJobTime = jobTimeSummary.calculateTotalJobTime,
        pipelineTimings = Nil
      ))
    )

  }

  "CVModelTrainResultHandler" should {

    "handle HandleJobResult message, update model and launch evaluation (no next step)" in new Setup {
      cvModelCommonService.loadModelMandatory(model.id) shouldReturn future(model)
      cvModelCommonService.assertModelStatus(model, CVModelStatus.Training) shouldReturn Try(())
      experimentCommonService.loadExperimentMandatory(experiment.id) shouldReturn future(experiment)
      cortexJobService.getJobOutputPath(jobId) shouldReturn future(outputPath)
      cortexJobService.getJobTimeSummary(eqTo(jobId)) shouldReturn future(jobTimeSummary)
      jobMetaService.readRawMeta(jobId, outputPath) shouldReturn future(jobResult.toByteArray)
      cortexJobService.buildPipelineTimings(*) shouldCall realMethod
      modelDao.update(model.id, *)(*) shouldAnswer { (_: String, updater: CVModel => CVModel) =>
        updater(model.entity)
        future(Some(model))
      }
      cvModelCommonService.populateOutputAlbumIfNeeded(*, *, *) shouldReturn future(())
      cvModelCommonService.updatePredictionTableColumnsAndCalculateStatistics(
        *,
        *,
        *,
        *
      ).shouldReturn(future(()))
      cvModelCommonService.populateSampleDAAlbum(*, *, *) shouldReturn future(())
      cvModelCommonService.updateModelStatus(model.id, CVModelStatus.Predicting) shouldReturn future(model)
      cvModelCommonService.buildAugmentationSummary(*) shouldCall realMethod
      experimentCommonService.getExperimentResultAs[CVTLTrainResult](
        experiment.entity
      ) shouldReturn Try(Some(trainResult))
      experimentDao.update(experiment.id, *)(*) shouldReturn future(Some(experiment))
      cvModelTrainPipelineHandler.launchEvaluation(*, *) shouldReturn future(())

      whenReady(handler ? HandleJobResult(
        jobId = jobId,
        lastStatus = CortexJobStatus.Completed,
        rawMeta = Json.toJsObject(CVModelTrainResultHandler.Meta(
          modelId = model.id,
          inputAlbumId = inputAlbum.id,
          testInputAlbumId = Some(testInputAlbum.id),
          userId = user.id,
          experimentId = experiment.id,
          outputAlbumId = Some(outputAlbum.id),
          testOutputAlbumId = Some(testOutputAlbum.id),
          autoDASampleAlbumId = Some(autoDASampleAlbum.id),
          probabilityPredictionTableId = None,
          testProbabilityPredictionTableId = None,
          nextStepParams = None,
          evaluationsMeta = List.empty
        ))
      )) { _ =>
        cvModelCommonService.updateModelStatus(model.id, CVModelStatus.Predicting) wasCalled once
      }
    }

    "handle HandleJobResult message, update model and launch next step" in new Setup {

      val featureExtractorType = CVModelRandomGenerator.randomTLModelType()
      val featureExtractor = CVModelRandomGenerator.randomModel(
        modelType = featureExtractorType
      )
      val stepTwoInputAlbum = randomAlbum()
      val stepTwoOutputAlbum = randomAlbum()
      val stepTwoPredictionTable = TableRandomGenerator.randomTable()
      val modelPipelineOperator = WithId(
        CVTLModelPrimitive(
          packageId = randomString(),
          name = "model operator",
          description = None,
          moduleName = "module1",
          className = "class2",
          cvTLModelPrimitiveType = CVTLModelPrimitiveType.Classifier,
          params = Seq.empty,
          isNeural = false
        ),
        randomString()
      )
      val modelPackage = WithId(
        DCProjectPackage(
          ownerId = None,
          dcProjectId = None,
          name = "my package",
          version = Some(Version(1, 0, 0, None)),
          location = None,
          created = Instant.now,
          description = None,
          isPublished = true
        ),
        randomString()
      )
      val fePipelineOperator = WithId(
        CVTLModelPrimitive(
          packageId = randomString(),
          name = "fe operator",
          description = None,
          moduleName = "module1",
          className = "class1",
          cvTLModelPrimitiveType = CVTLModelPrimitiveType.UTLP,
          params = Seq.empty,
          isNeural = false
        ),
        randomString()
      )

      cvModelCommonService.loadModelMandatory(model.id) shouldReturn future(model)
      cvModelCommonService.assertModelStatus(model, CVModelStatus.Training) shouldReturn Try(())
      experimentCommonService.loadExperimentMandatory(experiment.id) shouldReturn future(experiment)
      cortexJobService.getJobOutputPath(jobId) shouldReturn future(outputPath)
      cortexJobService.getJobTimeSummary(eqTo(jobId)) shouldReturn future(jobTimeSummary)
      jobMetaService.readRawMeta(jobId, outputPath) shouldReturn future(jobResult.toByteArray)
      cortexJobService.buildPipelineTimings(*) shouldCall realMethod
      modelDao.update(model.id, *)(*) shouldAnswer { (_: String, updater: CVModel => CVModel) =>
        updater(model.entity)
        future(Some(model))
      }
      cvModelCommonService.populateOutputAlbumIfNeeded(*, *, *) shouldReturn future(())
      cvModelCommonService.updatePredictionTableColumnsAndCalculateStatistics(
        *,
        *,
        *,
        *
      ).shouldReturn(future(()))
      cvModelCommonService.populateSampleDAAlbum(*, *, *) shouldReturn future(())
      cvModelCommonService.buildAugmentationSummary(*) shouldCall realMethod
      experimentCommonService.getExperimentResultAs[CVTLTrainResult](
        experiment.entity
      ) shouldReturn Try(Some(trainResult))
      experimentDao.update(experiment.id, *)(*) shouldReturn future(Some(experiment))
      cvModelTrainPipelineHandler.generateNewName(*, user.id) shouldReturn future(model.entity.name)
      imagesCommonService.getAlbumMandatory(stepTwoInputAlbum.id) shouldReturn future(stepTwoInputAlbum)
      cvModelTrainPipelineHandler.createOutputAlbumIfNeeded(
        stepTwoInputAlbum.entity,
        modelType.consumer,
        model.entity.name,
        user.id
      ) shouldReturn future(Some(stepTwoOutputAlbum))
      tableService.buildTableMeta(*) shouldCall realMethod
      cvModelCommonService.updatePredictionTableColumnsAndCalculateStatistics(
        *,
        *,
        *,
        *
      ).shouldReturn(future(()))
      cvModelTrainPipelineHandler.toTlModelType(*) shouldCall realMethod
      cvModelCommonService.loadModelMandatory(featureExtractor.id) shouldReturn future(featureExtractor)
      cvModelTrainPipelineHandler.createModel(
        name = model.entity.name,
        status = CVModelStatus.Training,
        description = experiment.entity.description,
        modelType = modelType.consumer,
        featureExtractorId = Some(featureExtractor.id),
        featureExtractorArchitecture = featureExtractorType.featureExtractorArchitecture,
        userId = user.id,
        experimentId = experiment.id
      ) shouldReturn future(model)
      cvModelCommonService.getCortexFeatureExtractorId(featureExtractor) shouldReturn Try("id")
      cvModelPrimitiveService.loadFeatureExtractorArchitecturePrimitive(
        featureExtractorType.featureExtractorArchitecture
      ) shouldReturn future(fePipelineOperator)
      packageService.loadPackageMandatory(fePipelineOperator.entity.packageId) shouldReturn future(modelPackage)
      cvModelPrimitiveService.loadTLConsumerPrimitive(modelType.consumer) shouldReturn future(modelPipelineOperator)
      packageService.loadPackageMandatory(modelPipelineOperator.entity.packageId) shouldReturn future(modelPackage)
      imagesCommonService.getPictures(stepTwoInputAlbum.id, false) shouldReturn future(Seq.empty)
      tableService.loadTableMandatory(stepTwoPredictionTable.id) shouldReturn future(stepTwoPredictionTable)
      imagesCommonService.convertPicturesToCortexTaggedImages(Seq.empty) shouldReturn Seq.empty
      imagesCommonService.getImagesPathPrefix(stepTwoInputAlbum.entity) shouldReturn "prefix"
      cvModelCommonService.buildCortexTLConsumer(modelType.consumer, *) shouldReturn TLModelType.defaultInstance
      cortexJobService.submitJob(*[CVModelTrainRequest], user.id) shouldReturn future(UUID.randomUUID)
      processService.startProcess(
        jobId = *,
        targetId = experiment.id,
        targetType = AssetType.Experiment,
        handlerClass = classOf[CVModelTrainResultHandler],
        meta = *[CVModelTrainResultHandler.Meta],
        userId = user.id
      ) shouldReturn future(ProcessRandomGenerator.randomProcess())

      whenReady(handler ? HandleJobResult(
        jobId = jobId,
        lastStatus = CortexJobStatus.Completed,
        rawMeta = Json.toJsObject(CVModelTrainResultHandler.Meta(
          modelId = model.id,
          inputAlbumId = inputAlbum.id,
          testInputAlbumId = Some(testInputAlbum.id),
          userId = user.id,
          experimentId = experiment.id,
          outputAlbumId = Some(outputAlbum.id),
          testOutputAlbumId = Some(testOutputAlbum.id),
          autoDASampleAlbumId = Some(autoDASampleAlbum.id),
          probabilityPredictionTableId = None,
          testProbabilityPredictionTableId = None,
          nextStepParams = Some(NextStepParams(
            featureExtractorId = featureExtractor.id,
            inputAlbumId = stepTwoInputAlbum.id,
            tuneFeatureExtractor = true,
            autoAugmentationParams = None,
            testInputAlbumId = None,
            modelParams = Map.empty,
            modelType = modelType.consumer,
            probabilityPredictionTableId = Some(stepTwoPredictionTable.id),
            testProbabilityPredictionTableId = None,
            trainParams = None
          )),
          evaluationsMeta = List.empty
        ))
      )) { _ =>
        cortexJobService.submitJob(*[CVModelTrainRequest], user.id) wasCalled once
      }
    }

    "handle HandleException message and update model and output albums" in new Setup {
      experimentCommonService.loadExperimentMandatory(experiment.id) shouldReturn future(experiment)
      experimentCommonService.getExperimentResultAs[CVTLTrainResult](experiment.entity) shouldReturn Try(None)
      cvModelTrainPipelineHandler.updateOutputEntitiesOnNoSuccess(*, ExperimentStatus.Error) shouldReturn future(())
      experimentDao.update(experiment.id, *)(*) shouldReturn future(Some(experiment))

      whenReady(handler ? HandleException(
        rawMeta = Json.toJsObject(CVModelTrainResultHandler.Meta(
          modelId = model.id,
          inputAlbumId = inputAlbum.id,
          testInputAlbumId = Some(testInputAlbum.id),
          userId = user.id,
          experimentId = experiment.id,
          outputAlbumId = Some(outputAlbum.id),
          testOutputAlbumId = Some(testOutputAlbum.id),
          autoDASampleAlbumId = Some(autoDASampleAlbum.id),
          probabilityPredictionTableId = None,
          testProbabilityPredictionTableId = None,
          nextStepParams = Some(NextStepParams(
            featureExtractorId = model.id,
            inputAlbumId = inputAlbum.id,
            tuneFeatureExtractor = false,
            autoAugmentationParams = Some(AutomatedAugmentationParams(
              augmentations = List(
                RotationParams(Seq(1.0f, 2.0f), true, 1),
                ShearingParams(Seq(10, 30), true, 1),
                NoisingParams(Seq(1, 2), 1),
                ZoomInParams(Seq(2), true, 1),
                ZoomOutParams(Seq(0.2f), true, 1),
                OcclusionParams(Seq(0.1f), OcclusionMode.Zero, true, 32, 1),
                TranslationParams(Seq(0.1f), TranslationMode.Constant, true, 1),
                SaltPepperParams(Seq(0.1f), 0.5f, 1),
                PhotometricDistortParams(PhotometricDistortAlphaBounds(0.5f, 1.5f), 18, 1),
                CroppingParams(Seq(0.25f), 1, false, 1),
                BlurringParams(Seq(0.5f), 1)
              ),
              bloatFactor = 42,
              generateSampleAlbum = false
            )),
            testInputAlbumId = None,
            modelParams = Map(
              "param1" -> StringParam("val1"),
              "param2" -> BooleanParam(true),
              "param3" -> FloatParam(42.1f),
              "param4" -> IntParam(4)
            ),
            modelType = modelType.consumer,
            probabilityPredictionTableId = None,
            testProbabilityPredictionTableId = None,
            trainParams = Some(CommonTrainParams(
              inputSize = Some(InputSize(20, 40)),
              loi = Some(Seq(
                LabelOfInterest("label1", 0.2),
                LabelOfInterest("label2", 0.3)
              )),
              defaultVisualThreshold = Some(0.42f),
              iouThreshold = Some(0.24f),
              featureExtractorLearningRate = Some(0.33d),
              modelLearningRate = Some(0.23d)
            ))
          )),
          evaluationsMeta = List.empty
      ))
      )) { _ =>
        cvModelTrainPipelineHandler.updateOutputEntitiesOnNoSuccess(*, ExperimentStatus.Error) wasCalled once
      }
    }
  }

}
