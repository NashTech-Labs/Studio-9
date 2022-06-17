package baile.services.cv.prediction

import java.util.UUID

import baile.dao.cv.prediction.CVPredictionDao
import baile.daocommons.WithId
import baile.domain.cv.EvaluateTimeSpentSummary
import baile.domain.cv.model.{ CVModel, CVModelSummary, CVModelType }
import baile.domain.cv.prediction.{ CVPrediction, CVPredictionStatus, PredictionTimeSpentSummary }
import baile.domain.job.{ CortexJobStatus, CortexJobTerminalStatus, CortexJobTimeSpentSummary }
import baile.services.cortex.job.{ CortexJobService, JobMetaService }
import baile.services.cv.model.CVModelCommonService
import baile.services.cv.prediction.CVPredictionResultHandler.Meta
import baile.services.images.ImagesCommonService
import baile.services.process.JobResultHandler
import baile.utils.TryExtensions._
import cortex.api.job.computervision.{ EvaluateResult, PredictResult }
import play.api.libs.json._

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

class CVPredictionResultHandler(
  cvModelCommonService: CVModelCommonService,
  cortexJobService: CortexJobService,
  jobMetaService: JobMetaService,
  imagesCommonService: ImagesCommonService,
  dao: CVPredictionDao
) extends JobResultHandler[Meta] {

  override protected val metaReads: Reads[Meta] = CVPredictionResultHandler.CVPredictionResultHandlerMetaFormat

  override protected def handleResult(
    jobId: UUID,
    lastStatus: CortexJobTerminalStatus,
    meta: Meta
  )(implicit ec: ExecutionContext): Future[Unit] = {

    def handleResult(
      model: WithId[CVModel],
      prediction: WithId[CVPrediction],
      rawJobResult: Array[Byte],
      jobTimeSpentSummary: CortexJobTimeSpentSummary
    ): Future[Unit] = {
      if (!meta.evaluate) handlePredictResult(model, prediction, rawJobResult, jobTimeSpentSummary, meta.userId)
      else handleEvaluateResult(model, prediction, rawJobResult, jobTimeSpentSummary, meta.userId)
    }

    def assertPredictionStatus(prediction: WithId[CVPrediction]): Try[Unit] = Try {
      if (prediction.entity.status != CVPredictionStatus.Running) {
        throw new RuntimeException(
          s"Unexpected prediction status ${ prediction.entity.status } for prediction${ prediction.id }." +
            s" Expected ${ CVPredictionStatus.Running }"
        )
      } else {
        ()
      }
    }

    lastStatus match {
      case CortexJobStatus.Completed =>
        for {
          prediction <- loadPredictionMandatory(meta.predictionId)
          _ <- assertPredictionStatus(prediction).toFuture
          model <- cvModelCommonService.loadModelMandatory(prediction.entity.modelId)
          outputPath <- cortexJobService.getJobOutputPath(jobId)
          jobSummary <- cortexJobService.getJobTimeSummary(jobId)
          rawJobResult <- jobMetaService.readRawMeta(jobId, outputPath)
          _ <- handleResult(model, prediction, rawJobResult, jobSummary)
          _ <- cvModelCommonService.activateAlbum(prediction.entity.outputAlbumId)
        } yield ()
      case CortexJobStatus.Cancelled | CortexJobStatus.Failed =>
        handleException(meta)
    }

  }

  override protected def handleException(meta: Meta): Future[Unit] =
    for {
      prediction <- loadPredictionMandatory(meta.predictionId)
      _ <- cvModelCommonService.failAlbum(prediction.entity.outputAlbumId)
      _ <- dao.update(prediction.id, _.copy(status = CVPredictionStatus.Error))
    } yield ()

  private def handlePredictResult(
    model: WithId[CVModel],
    prediction: WithId[CVPrediction],
    rawJobResult: Array[Byte],
    jobTimeSummary: CortexJobTimeSpentSummary,
    userId: UUID
  ): Future[Unit] = {
    for {
      predictResult <- Try(PredictResult.parseFrom(rawJobResult)).toFuture
      _ <- model.entity.`type` match {
        case CVModelType.TL(_: CVModelType.TLConsumer.Decoder, _) =>
          cvModelCommonService.populateDecoderOutputAlbum(
            prediction.entity.inputAlbumId,
            prediction.entity.outputAlbumId,
            predictResult.images
          )
        case _ => cvModelCommonService.populateOutputAlbumIfNeeded(
          prediction.entity.inputAlbumId,
          Some(prediction.entity.outputAlbumId),
          predictResult.images
        )
      }
      _ <- cvModelCommonService.updatePredictionTableColumnsAndCalculateStatistics(
        probabilityPredictionTableId = prediction.entity.probabilityPredictionTableId,
        probabilityPredictionTableSchema = predictResult.probabilityPredictionTableSchema,
        modelType = model.entity.`type`,
        userId = userId
      )
      _ <- model.entity.`type` match {
        case CVModelType.TL(_: CVModelType.TLConsumer.Localizer, _) => cvModelCommonService.updateAlbumVideo(
          prediction.entity.outputAlbumId,
          _.map(_.copy(fileSize = predictResult.videoFileSize.getOrElse(throw new RuntimeException(
            s"Video file size was not found while handling prediction result for prediction ${ prediction.id }"
          ))))
        )
        case _ => Future.unit
      }
      _ <- dao.update(
        prediction.id,
        _.copy(
          predictionTimeSpentSummary = Some(PredictionTimeSpentSummary(
            dataFetchTime = predictResult.dataFetchTime,
            loadModelTime = predictResult.loadModelTime,
            predictionTime = predictResult.predictionTime,
            tasksQueuedTime = jobTimeSummary.tasksQueuedTime,
            totalJobTime = jobTimeSummary.calculateTotalJobTime,
            pipelineTimings = cortexJobService.buildPipelineTimings(predictResult.pipelineTimings)
          )),
          status = CVPredictionStatus.Done
        )
      )
    } yield ()
  }

  private def handleEvaluateResult(
    model: WithId[CVModel],
    prediction: WithId[CVPrediction],
    rawJobResult: Array[Byte],
    jobTimeSummary: CortexJobTimeSpentSummary,
    userId: UUID
  ): Future[Unit] = {
    for {
      evaluateResult <- Try(EvaluateResult.parseFrom(rawJobResult)).toFuture
      _ <- cvModelCommonService.populateOutputAlbumIfNeeded(
        prediction.entity.inputAlbumId,
        Some(prediction.entity.outputAlbumId),
        evaluateResult.images
      )
      _ <- cvModelCommonService.updatePredictionTableColumnsAndCalculateStatistics(
        probabilityPredictionTableId = prediction.entity.probabilityPredictionTableId,
        probabilityPredictionTableSchema = evaluateResult.probabilityPredictionTableSchema,
        modelType = model.entity.`type`,
        userId = userId
      )
      _ <- dao.update(
        prediction.id,
        _.copy(
          evaluationSummary = Some(CVModelSummary(
            labels = evaluateResult.confusionMatrix.fold[Seq[String]](Seq.empty)(_.labels),
            confusionMatrix = evaluateResult.confusionMatrix.map(_.confusionMatrixCells.map(
              CVModelCommonService.buildConfusionMatrixCell
            )),
            mAP = evaluateResult.map,
            reconstructionLoss = None
          )),
          evaluateTimeSpentSummary = Some(EvaluateTimeSpentSummary(
            dataFetchTime = evaluateResult.dataFetchTime,
            loadModelTime = evaluateResult.loadModelTime,
            scoreTime = evaluateResult.scoreTime,
            tasksQueuedTime = jobTimeSummary.tasksQueuedTime,
            totalJobTime = jobTimeSummary.calculateTotalJobTime,
            pipelineTimings = cortexJobService.buildPipelineTimings(evaluateResult.pipelineTimings)
          )),
          status = CVPredictionStatus.Done
        )
      )
    } yield ()
  }

  private def loadPredictionMandatory(predictionId: String): Future[WithId[CVPrediction]] =
    dao.get(predictionId).map(_.getOrElse(throw new RuntimeException(
      s"Not found prediction $predictionId in storage while handling process result"
    )))

}

object CVPredictionResultHandler {

  case class Meta(predictionId: String, evaluate: Boolean, userId: UUID)

  implicit val CVPredictionResultHandlerMetaFormat: OFormat[Meta] = Json.format[Meta]

}
