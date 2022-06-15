package baile.services.tabular.prediction

import java.util.UUID

import akka.event.LoggingAdapter
import baile.dao.tabular.prediction.TabularPredictionDao
import baile.daocommons.WithId
import baile.domain.job.{ CortexJobStatus, CortexJobTerminalStatus }
import baile.domain.table._
import baile.domain.tabular.prediction.{ TabularPrediction, TabularPredictionStatus }
import baile.services.cortex.job.{ CortexJobService, JobMetaService }
import baile.services.process.JobResultHandler
import baile.services.table.TableService
import baile.services.tabular.model.TabularModelCommonService
import baile.services.tabular.prediction.TabularPredictionResultHandler.Meta
import baile.utils.TryExtensions._
import cortex.api.job.tabular.PredictionResult
import play.api.libs.json._

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

class TabularPredictionResultHandler(
  tabularModelCommonService: TabularModelCommonService,
  cortexJobService: CortexJobService,
  jobMetaService: JobMetaService,
  tableService: TableService,
  dao: TabularPredictionDao,
  logger: LoggingAdapter
) extends JobResultHandler[Meta] {

  override protected val metaReads: Reads[Meta] = TabularPredictionResultHandler
    .TabularPredictionResultHandlerMetaFormat

  override protected def handleResult(
    jobId: UUID,
    lastStatus: CortexJobTerminalStatus,
    meta: Meta
  )(implicit ec: ExecutionContext): Future[Unit] = {

    def assertPredictionStatus(prediction: WithId[TabularPrediction]): Try[Unit] = Try {
      if (prediction.entity.status != TabularPredictionStatus.Running) {
        throw new RuntimeException(
          s"Unexpected prediction status ${ prediction.entity.status } for prediction${ prediction.id }." +
            s" Expected ${ TabularPredictionStatus.Running } "
        )
      } else {
        ()
      }
    }

    for {
      prediction <- loadPredictionMandatory(meta.predictionId)
      _ <- assertPredictionStatus(prediction).toFuture
      _ <- lastStatus match {
        case CortexJobStatus.Completed =>
          for {
            outputPath <- cortexJobService.getJobOutputPath(jobId)
            rawJobResult <- jobMetaService.readRawMeta(jobId, outputPath)
            _ <- Try(PredictionResult.parseFrom(rawJobResult)).toFuture
            _ <- tableService.updateStatus(prediction.entity.outputTableId, TableStatus.Active)
            _ <- dao.update(prediction.id, _.copy(status = TabularPredictionStatus.Done))
            _ <- tableService.calculateColumnStatistics(prediction.entity.outputTableId, None, meta.userId)
          } yield ()
        case CortexJobStatus.Cancelled | CortexJobStatus.Failed =>
          for {
            _ <- tabularModelCommonService.failTable(prediction.entity.outputTableId)
            _ <- dao.update(prediction.id, _.copy(status = TabularPredictionStatus.Error))
          } yield ()
      }
    } yield ()

  }

  override protected def handleException(meta: Meta): Future[Unit] =
    for {
      prediction <- loadPredictionMandatory(meta.predictionId)
      _ <- tabularModelCommonService.failTable(prediction.entity.outputTableId)
      _ <- dao.update(prediction.id, _.copy(status = TabularPredictionStatus.Error))
    } yield ()

  private def loadPredictionMandatory(predictionId: String): Future[WithId[TabularPrediction]] =
    dao.get(predictionId).map(_.getOrElse(throw new RuntimeException(
      s"Not found prediction $predictionId in storage while handling process result"
    )))

}

object TabularPredictionResultHandler {

  case class Meta(predictionId: String, userId: UUID)

  implicit val TabularPredictionResultHandlerMetaFormat: OFormat[Meta] = Json.format[Meta]

}
