package baile.services.cv.prediction

import java.time.Instant
import java.util.UUID

import akka.actor.Props
import akka.pattern.ask
import baile.ExtendedBaseSpec
import baile.RandomGenerators._
import baile.dao.cv.prediction.CVPredictionDao
import baile.daocommons.WithId
import baile.domain.cv.model.CVModelType
import baile.domain.cv.prediction.{ CVPrediction, CVPredictionStatus }
import baile.domain.job.{ CortexJobStatus, CortexJobTimeSpentSummary, CortexTimeInfo }
import baile.services.cortex.job.{ CortexJobService, JobMetaService }
import baile.services.cv.model.{ CVModelCommonService, CVModelRandomGenerator }
import baile.services.images.ImagesCommonService
import baile.services.images.util.ImagesRandomGenerator
import baile.services.process.JobResultHandler.{ HandleException, HandleJobResult }
import baile.services.usermanagement.util.TestData
import cortex.api.job.album.common.Image
import cortex.api.job.common.{ ConfusionMatrix, ConfusionMatrixCell }
import cortex.api.job.computervision.{ EvaluateResult, PredictResult, PredictedImage }
import play.api.libs.json.Json

class CVPredictionResultHandlerSpec extends ExtendedBaseSpec {

  trait Setup {

    val cvModelCommonService = mock[CVModelCommonService]
    val cortexJobService = mock[CortexJobService]
    val jobMetaService = mock[JobMetaService]
    val imagesCommonService = mock[ImagesCommonService]
    val predictionDao = mock[CVPredictionDao]

    val handler = system.actorOf(Props(
      new CVPredictionResultHandler(
        cvModelCommonService,
        cortexJobService,
        jobMetaService,
        imagesCommonService,
        predictionDao
      )
    ))

    val user = TestData.SampleUser
    val jobId = UUID.randomUUID()
    val inputAlbum = ImagesRandomGenerator.randomAlbum()
    val outputAlbum = ImagesRandomGenerator.randomAlbum()
    val model = CVModelRandomGenerator.randomModel(
      modelType = CVModelType.TL(CVModelType.TLConsumer.Localizer("RFB_NET"), "SCAE")
    )
    val prediction = WithId(
      CVPrediction(
        ownerId = user.id,
        name = "name",
        status = CVPredictionStatus.Running,
        created = Instant.now,
        updated = Instant.now,
        description = Some("desc"),
        modelId = model.id,
        inputAlbumId = inputAlbum.id,
        outputAlbumId = outputAlbum.id,
        evaluationSummary = None,
        predictionTimeSpentSummary = None,
        evaluateTimeSpentSummary = None,
        probabilityPredictionTableId = None,
        cvModelPredictOptions = None
      ),
      randomString()
    )
    val jobTimeSummary = CortexJobTimeSpentSummary(
      tasksQueuedTime = 10L,
      jobTimeInfo = CortexTimeInfo(Instant.now(), Instant.now(), Instant.now()),
      Seq.empty
    )
    val outputPath = randomString()

    predictionDao.get(prediction.id)(*) shouldReturn future(Some(prediction))
    predictionDao.update(prediction.id, *)(*) shouldAnswer { (_: String, updater: CVPrediction => CVPrediction) =>
      future(Some(WithId(updater(prediction.entity), prediction.id)))
    }
  }

  "CVPredictionResultHandler" should {

    "handle HandleJobResult, update prediction and populate output album when prediction is done" in new Setup {
      val jobResult = PredictResult(
        images = Seq(
          PredictedImage(Some(Image("img1.png", Some("i1"), Some(12L)))),
          PredictedImage(Some(Image("img2.png", Some("i2"), Some(2L)))),
          PredictedImage(Some(Image("img3.png", Some("i3"), Some(3L))))
        ),
        videoFileSize = None,
        dataFetchTime = 23L,
        loadModelTime = 204L,
        predictionTime = 5585L
      )

      cvModelCommonService.loadModelMandatory(model.id) shouldReturn future(model)
      cortexJobService.getJobOutputPath(jobId) shouldReturn future(outputPath)
      cortexJobService.getJobTimeSummary(jobId) shouldReturn future(jobTimeSummary)
      jobMetaService.readRawMeta(jobId, outputPath) shouldReturn future(jobResult.toByteArray)
      cvModelCommonService.populateOutputAlbumIfNeeded(
        prediction.entity.inputAlbumId,
        Some(prediction.entity.outputAlbumId),
        *
      ) shouldReturn future(())
      cvModelCommonService.updatePredictionTableColumnsAndCalculateStatistics(
        *,
        *,
        *,
        *
      ).shouldReturn(future(()))
      cvModelCommonService.updateAlbumVideo(prediction.entity.outputAlbumId, *) shouldReturn future(Some(outputAlbum))
      cvModelCommonService.activateAlbum(prediction.entity.outputAlbumId) shouldReturn future(Some(outputAlbum))
      cortexJobService.buildPipelineTimings(*) shouldReturn List.empty

      whenReady(handler ? HandleJobResult(
        jobId = jobId,
        lastStatus = CortexJobStatus.Completed,
        rawMeta = Json.toJsObject(CVPredictionResultHandler.Meta(
          predictionId = prediction.id,
          evaluate = false,
          userId = user.id
        ))
      )) { _ =>
        cvModelCommonService.populateOutputAlbumIfNeeded(
          prediction.entity.inputAlbumId,
          Some(prediction.entity.outputAlbumId),
          *
        ) wasCalled once
        predictionDao.update(prediction.id, *)(*) wasCalled once
      }
    }

    "handle HandleJobResult, update prediction and populate output album when evaluation is done" in new Setup {
      val jobResult = EvaluateResult(
        images = Seq(
          PredictedImage(Some(Image("img1.png", Some("i1"), Some(12L)))),
          PredictedImage(Some(Image("img2.png", Some("i2"), Some(2L)))),
          PredictedImage(Some(Image("img3.png", Some("i3"), Some(3L))))
        ),
        confusionMatrix = Some(ConfusionMatrix(
          confusionMatrixCells = Seq(
            ConfusionMatrixCell(Some(0), Some(0), 1),
            ConfusionMatrixCell(Some(1), Some(0), 0),
            ConfusionMatrixCell(Some(0), Some(1), 1),
            ConfusionMatrixCell(Some(1), Some(1), 1)
          ),
          labels = Seq("label1", "label2")
        )),
        map = Some(0.12),
        dataFetchTime = 100,
        loadModelTime = 200,
        scoreTime = 400
      )

      cvModelCommonService.loadModelMandatory(model.id) shouldReturn future(model)
      cortexJobService.getJobOutputPath(jobId) shouldReturn future(outputPath)
      cortexJobService.getJobTimeSummary(jobId) shouldReturn future(jobTimeSummary)
      jobMetaService.readRawMeta(jobId, outputPath) shouldReturn future(jobResult.toByteArray)
      cvModelCommonService.populateOutputAlbumIfNeeded(
        prediction.entity.inputAlbumId,
        Some(prediction.entity.outputAlbumId),
        *
      ) shouldReturn future(())
      cvModelCommonService.updatePredictionTableColumnsAndCalculateStatistics(
        *,
        *,
        *,
        *
      ).shouldReturn(future(()))
      cvModelCommonService.updateAlbumVideo(prediction.entity.outputAlbumId, *) shouldReturn future(Some(outputAlbum))
      cvModelCommonService.activateAlbum(prediction.entity.outputAlbumId) shouldReturn future(Some(outputAlbum))
      cortexJobService.buildPipelineTimings(*) shouldReturn List.empty

      whenReady(handler ? HandleJobResult(
        jobId = jobId,
        lastStatus = CortexJobStatus.Completed,
        rawMeta = Json.toJsObject(CVPredictionResultHandler.Meta(
          predictionId = prediction.id,
          evaluate = false,
          userId = user.id
        ))
      )) { _ =>
        cvModelCommonService.populateOutputAlbumIfNeeded(
          prediction.entity.inputAlbumId,
          Some(prediction.entity.outputAlbumId),
          *
        ) wasCalled once
        predictionDao.update(prediction.id, *)(*) wasCalled once
      }
    }

    "handle HandleJobResult, update prediction and populate output album " +
      "when prediction is done for decoder" in new Setup {
      val decoderModel = CVModelRandomGenerator.randomModel(
        modelType = CVModelType.TL(CVModelType.TLConsumer.Decoder("SCAE"), "SCAE")
      )
      val jobResult = PredictResult(
        images = Seq(
          PredictedImage(Some(Image("img1.png", Some("i1"), Some(12L)))),
          PredictedImage(Some(Image("img2.png", Some("i2"), Some(2L)))),
          PredictedImage(Some(Image("img3.png", Some("i3"), Some(3L))))
        ),
        videoFileSize = None,
        dataFetchTime = 23L,
        loadModelTime = 204L,
        predictionTime = 5585L
      )

      cvModelCommonService.loadModelMandatory(prediction.entity.modelId) shouldReturn future(decoderModel)
      cortexJobService.getJobOutputPath(jobId) shouldReturn future(outputPath)
      cortexJobService.getJobTimeSummary(jobId) shouldReturn future(jobTimeSummary)
      jobMetaService.readRawMeta(jobId, outputPath) shouldReturn future(jobResult.toByteArray)
      cvModelCommonService.populateDecoderOutputAlbum(
        prediction.entity.inputAlbumId,
        prediction.entity.outputAlbumId,
        *
      ) shouldReturn future(())
      cvModelCommonService.updatePredictionTableColumnsAndCalculateStatistics(
        *,
        *,
        *,
        *
      ).shouldReturn(future(()))
      cvModelCommonService.activateAlbum(prediction.entity.outputAlbumId) shouldReturn future(Some(outputAlbum))
      cortexJobService.buildPipelineTimings(*) shouldReturn List.empty

      whenReady(handler ? HandleJobResult(
        jobId = jobId,
        lastStatus = CortexJobStatus.Completed,
        rawMeta = Json.toJsObject(CVPredictionResultHandler.Meta(
          predictionId = prediction.id,
          evaluate = false,
          userId = user.id
        ))
      )) { _ =>
        cvModelCommonService.populateDecoderOutputAlbum(
          prediction.entity.inputAlbumId,
          prediction.entity.outputAlbumId,
          *
        ) wasCalled once
        predictionDao.update(prediction.id, *)(*) wasCalled once
      }
    }

    "handle HandleException message and update model and output albums" in new Setup {
      cvModelCommonService.failAlbum(prediction.entity.outputAlbumId) shouldReturn future(Some(outputAlbum))
      whenReady(handler ? HandleException(
        rawMeta = Json.toJsObject(CVPredictionResultHandler.Meta(
          predictionId = prediction.id,
          evaluate = true,
          userId = user.id
        ))
      )) { _ =>
        cvModelCommonService.failAlbum(prediction.entity.outputAlbumId) wasCalled once
        predictionDao.update(prediction.id, *)(*) wasCalled once
      }
    }

  }

}
