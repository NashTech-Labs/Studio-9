package baile.services.cv.model

import java.time.Instant
import java.util.UUID

import akka.actor.Props
import akka.pattern.ask
import baile.dao.experiment.ExperimentDao
import baile.daocommons.WithId
import baile.domain.common.ConfusionMatrixCell
import baile.domain.pipeline.PipelineParams.{ BooleanParam, FloatParam, IntParam, StringParam }
import baile.domain.cv.model.{ CVModelType, _ }
import baile.domain.cv.pipeline.{ CVTLTrainPipeline, CVTLTrainStep1Params, CVTLTrainStep2Params, FeatureExtractorParams }
import baile.domain.cv.result.{ CVTLTrainResult, CVTLTrainStepResult }
import baile.domain.cv.EvaluateTimeSpentSummary
import baile.domain.experiment.ExperimentStatus
import baile.domain.images.augmentation.{ BlurringParams, PhotometricDistortAlphaBounds, PhotometricDistortParams }
import baile.domain.images.{ Album, AlbumLabelMode, AlbumStatus }
import baile.domain.job.{ CortexJobStatus, CortexJobTimeSpentSummary, CortexTimeInfo, PipelineTiming }
import baile.services.cortex.job.{ CortexJobService, JobMetaService }
import baile.services.cv.model.CVModelEvaluateResultHandler.StepMeta
import baile.services.experiment.{ ExperimentCommonService, ExperimentRandomGenerator }
import baile.services.images.util.ImagesRandomGenerator.randomAlbum
import baile.services.process.JobResultHandler.{ HandleException, HandleJobResult }
import baile.services.usermanagement.util.TestData
import baile.{ ExtendedBaseSpec, RandomGenerators }
import cats.data.NonEmptyList
import cortex.api.job.album.common.Image
import cortex.api.job.computervision.{ EvaluateResult, PredictedImage }
import cortex.api.job.common.{
  ConfusionMatrix => CortexConfusionMatrix,
  ConfusionMatrixCell => CortexConfusionMatrixCell
}
import play.api.libs.json.Json

import scala.util.Try

class CVModelEvaluateResultHandlerSpec extends ExtendedBaseSpec {

  trait Setup {

    val experimentDao = mock[ExperimentDao]
    val experimentCommonService = mock[ExperimentCommonService]
    val cvModelTrainPipelineHandler = mock[CVModelTrainPipelineHandler]
    val cortexJobService = mock[CortexJobService]
    val jobMetaService = mock[JobMetaService]
    val cvModelCommonService = mock[CVModelCommonService]

    val handler = system.actorOf(Props(
      new CVModelEvaluateResultHandler(
        experimentDao,
        experimentCommonService,
        cvModelTrainPipelineHandler,
        cortexJobService,
        jobMetaService,
        cvModelCommonService
      )
    ))

    val user = TestData.SampleUser
    val jobId = UUID.randomUUID()

    val inputAlbum: WithId[Album] = randomAlbum(
      status = AlbumStatus.Active,
      labelMode = AlbumLabelMode.Classification
    )
    val outputAlbum: WithId[Album] = randomAlbum(
      status = AlbumStatus.Saving,
      labelMode = inputAlbum.entity.labelMode
    )
    val testInputAlbum: WithId[Album] = randomAlbum(
      status = AlbumStatus.Active,
      labelMode = inputAlbum.entity.labelMode
    )
    val testOutputAlbum: WithId[Album] = randomAlbum(
      status = AlbumStatus.Saving,
      labelMode = testInputAlbum.entity.labelMode
    )
    val autoAugmentationParams = AutomatedAugmentationParams(
      augmentations = List(
        BlurringParams(List(1, 2, 3), 4),
        PhotometricDistortParams(PhotometricDistortAlphaBounds(1, 3), 5, 2)
      ),
      bloatFactor = 11,
      generateSampleAlbum = true
    )
    val autoDASampleAlbum = randomAlbum(
      status = AlbumStatus.Saving,
      labelMode = inputAlbum.entity.labelMode
    )

    val model = CVModelRandomGenerator.randomModel(
      status = CVModelStatus.Predicting
    )
    val outputPath = RandomGenerators.randomString()

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

    val jobTimeSummary = CortexJobTimeSpentSummary(
      tasksQueuedTime = 10L,
      jobTimeInfo = CortexTimeInfo(Instant.now(), Instant.now(), Instant.now()),
      Seq.empty
    )

    val feParams: FeatureExtractorParams = FeatureExtractorParams.CreateNewFeatureExtractorParams(
      "feArch",
      Map.empty
    )

    val pipelineStepOne = CVTLTrainStep1Params(
      feParams = feParams,
      modelType = CVModelType.TLConsumer.Classifier("classifier"),
      modelParams = Map(
        "stringParams" -> StringParam("test"),
        "intParams" -> IntParam(1),
        "floatParams" -> FloatParam(1.2f),
        "booleanParams" -> BooleanParam(true)
      ),
      inputAlbumId = RandomGenerators.randomString(),
      testInputAlbumId = None,
      automatedAugmentationParams = None,
      trainParams = None
    )

    val pipelineStepTwo = CVTLTrainStep2Params(
      tuneFeatureExtractor = false,
      modelType = CVModelType.TLConsumer.Classifier("classifer"),
      modelParams = Map(
        "stringParams" -> StringParam("test"),
        "intParams" -> IntParam(1),
        "floatParams" -> FloatParam(1.2f),
        "booleanParams" -> BooleanParam(true)
      ),
      inputAlbumId = RandomGenerators.randomString(),
      testInputAlbumId = None,
      automatedAugmentationParams = None,
      trainParams = None
    )

    val pipeline = CVTLTrainPipeline(
      pipelineStepOne,
      Some(pipelineStepTwo)
    )

    val trainStepResult = CVTLTrainStepResult(
      modelId = model.id,
      outputAlbumId = Some(RandomGenerators.randomString()),
      testOutputAlbumId = Some(testOutputAlbum.id),
      autoAugmentationSampleAlbumId = Some(autoDASampleAlbum.id),
      summary = Some(CVModelSummary(
        Seq.empty[String],
        None,
        None,
        None
      )),
      testSummary = None,
      augmentationSummary = None,
      trainTimeSpentSummary = None,
      evaluateTimeSpentSummary = None,
      probabilityPredictionTableId = None,
      testProbabilityPredictionTableId = None
    )

    val trainResult = CVTLTrainResult(
      trainStepResult,
      None
    )

    val experiment = ExperimentRandomGenerator.randomExperiment(
      id = "experiment-id-123",
      status = ExperimentStatus.Running,
      pipeline = pipeline,
      result = Some(trainResult)
    )

    val meta = StepMeta(
      modelId = model.id,
      experimentId = UUID.randomUUID().toString,
      testInputAlbumId = RandomGenerators.randomString(),
      userId = user.id,
      probabilityPredictionTableId = None
    )

    val pipelineTimings = List(PipelineTiming("step1", 20l), PipelineTiming("step2", 20l))

    val jobResult = EvaluateResult(
      images = Seq(
        PredictedImage(Some(Image("img1.png", Some("i1"), Some(12L)))),
        PredictedImage(Some(Image("img2.png", Some("i2"), Some(2L)))),
        PredictedImage(Some(Image("img3.png", Some("i3"), Some(3L))))
      ),
      confusionMatrix = Some(cortexConfusionMatrix),
      map = Some(0.12),
      dataFetchTime = 100,
      loadModelTime = 200,
      scoreTime = 400,
      pipelineTimings = Map(
        "step1" -> 20l,
        "step2" -> 20l
      )
    )
    val evaluationTimeSummary = EvaluateTimeSpentSummary(
      dataFetchTime = jobResult.dataFetchTime,
      loadModelTime = jobResult.loadModelTime,
      scoreTime = jobResult.scoreTime,
      tasksQueuedTime = jobTimeSummary.tasksQueuedTime,
      totalJobTime = jobTimeSummary.calculateTotalJobTime,
      pipelineTimings = pipelineTimings
    )
    val updatedTrainStepResult = trainStepResult.copy(
      evaluateTimeSpentSummary = Some(evaluationTimeSummary),
      testSummary = Some(CVModelSummary(
        labels = labels,
        confusionMatrix = Some(confusionMatrix),
        mAP = jobResult.map,
        reconstructionLoss = None
      ))
    )
  }

  "CVModelEvaluateResultHandler" should {

    "handle HandleJobResult message and update model" in new Setup {
      cvModelCommonService.loadModelMandatory(model.id) shouldReturn future(model)
      cvModelCommonService.assertModelStatus(model, CVModelStatus.Predicting) shouldReturn Try(())
      experimentCommonService.loadExperimentMandatory(experiment.id) shouldReturn future(experiment)
      cortexJobService.getJobOutputPath(jobId) shouldReturn future(outputPath)
      cortexJobService.getJobTimeSummary(eqTo(jobId)) shouldReturn future(jobTimeSummary)
      jobMetaService.readRawMeta(jobId, outputPath) shouldReturn future(jobResult.toByteArray)
      cortexJobService.buildPipelineTimings(*) shouldCall realMethod
      cvModelCommonService.populateOutputAlbumIfNeeded(
        testInputAlbum.id,
        Some(testOutputAlbum.id),
        *
      ) shouldReturn future(())
      cvModelCommonService.updatePredictionTableColumnsAndCalculateStatistics(
       *,
       *,
       *,
       *
      ).shouldReturn(future(()))
      experimentCommonService.getExperimentResultAs[CVTLTrainResult](
        experiment.entity
      ) shouldReturn Try(Some(trainResult))
      experimentDao.update(experiment.id, *)(*) shouldReturn future(Some(experiment))
      cvModelTrainPipelineHandler.updateOutputEntitiesOnSuccess(*) shouldReturn future(())

      whenReady(handler ? HandleJobResult(
        jobId = jobId,
        lastStatus = CortexJobStatus.Completed,
        rawMeta = Json.toJsObject(CVModelEvaluateResultHandler.Meta(NonEmptyList.one(StepMeta(
          modelId = model.id,
          experimentId = experiment.id,
          testInputAlbumId = testInputAlbum.id,
          userId = user.id,
          probabilityPredictionTableId = None
        ))))
      )) { _ =>
        cvModelTrainPipelineHandler.updateOutputEntitiesOnSuccess(*) wasCalled once
      }
    }

    "handle HandleException message and update model and output albums" in new Setup {
      experimentCommonService.loadExperimentMandatory(experiment.id) shouldReturn future(experiment)
      experimentCommonService.getExperimentResultAs[CVTLTrainResult](
        experiment.entity
      ) shouldReturn Try(Some(trainResult))
      cvModelTrainPipelineHandler.updateOutputEntitiesOnNoSuccess(*, ExperimentStatus.Error) shouldReturn future(())
      experimentDao.update(experiment.id, *)(*) shouldReturn future(Some(experiment))
      whenReady(handler ? HandleException(
        rawMeta = Json.toJsObject(CVModelEvaluateResultHandler.Meta(NonEmptyList.one(StepMeta(
          modelId = model.id,
          experimentId = experiment.id,
          testInputAlbumId = testInputAlbum.id,
          userId = user.id,
          probabilityPredictionTableId = None
        ))))
      )) { _ =>
        cvModelTrainPipelineHandler.updateOutputEntitiesOnNoSuccess(*, ExperimentStatus.Error) wasCalled once
      }
    }

  }

}
