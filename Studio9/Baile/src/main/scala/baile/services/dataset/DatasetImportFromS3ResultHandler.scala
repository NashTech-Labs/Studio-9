package baile.services.dataset

import java.util.UUID

import akka.event.LoggingAdapter
import baile.dao.dataset.DatasetDao
import baile.domain.dataset.DatasetStatus
import baile.domain.job.{ CortexJobStatus, CortexJobTerminalStatus }
import baile.services.cortex.job.{ CortexJobService, JobMetaService }
import baile.services.dataset.DatasetImportFromS3ResultHandler.Meta
import baile.services.process.JobResultHandler
import cortex.api.job.dataset.S3DatasetImportResponse
import play.api.libs.json.{ Json, OFormat, Reads }

import scala.concurrent.{ ExecutionContext, Future }

class DatasetImportFromS3ResultHandler(
  cortexJobService: CortexJobService,
  jobMetaService: JobMetaService,
  dao: DatasetDao,
  logger: LoggingAdapter
) extends JobResultHandler[Meta] {

  override protected val metaReads: Reads[Meta] =
    DatasetImportFromS3ResultHandler.DatasetImportFromS3ResultHandlerMetaFormat

  override protected def handleResult(
    jobId: UUID,
    lastStatus: CortexJobTerminalStatus,
    meta: Meta
  )(implicit ec: ExecutionContext): Future[Unit] = {

    lastStatus match {
      case CortexJobStatus.Completed =>
        for {
          outputPath <- cortexJobService.getJobOutputPath(jobId)
          _ <- jobMetaService.readRawMeta(jobId, outputPath).map(S3DatasetImportResponse.parseFrom)
          _ <- markDatasetActive(meta.datasetId)
        } yield ()
      case CortexJobStatus.Failed | CortexJobStatus.Cancelled =>
        logger.info("Upload job {} for dataset id: {}", lastStatus.toString, meta.datasetId)
        handleException(meta)
    }
  }

  override protected def handleException(meta: Meta): Future[Unit] = {
    markDatasetActive(meta.datasetId)
  }

  private def markDatasetActive(datasetId: String): Future[Unit] =
    dao.update(
      datasetId,
      _.copy(
        status = DatasetStatus.Active
      )
    ).map(_ => ())

}

object DatasetImportFromS3ResultHandler {

  case class Meta(datasetId: String)

  implicit val DatasetImportFromS3ResultHandlerMetaFormat: OFormat[Meta] = Json.format[Meta]

}
