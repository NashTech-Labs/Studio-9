package baile.services.cv.model

import java.util.UUID

import baile.dao.cv.model.CVModelDao
import baile.domain.common.CortexModelReference
import baile.domain.cv.model.CVModelStatus
import baile.domain.job.{ CortexJobStatus, CortexJobTerminalStatus }
import baile.services.common.MLEntityExportImportService
import baile.services.cortex.job.{ CortexJobService, JobMetaService }
import baile.services.cv.model.CVModelImportResultHandler.Meta
import baile.services.process.JobResultHandler
import baile.utils.TryExtensions._
import cortex.api.job.computervision.CVModelImportResult
import play.api.libs.json.{ Json, OFormat, Reads }

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

class CVModelImportResultHandler(
  cvModelCommonService: CVModelCommonService,
  importService: MLEntityExportImportService,
  cortexJobService: CortexJobService,
  jobMetaService: JobMetaService,
  modelDao: CVModelDao
) extends JobResultHandler[Meta] {

  override protected val metaReads: Reads[Meta] = CVModelImportResultHandler.CVModelImportResultHandlerMetaFormat

  override protected def handleResult(
    jobId: UUID,
    lastStatus: CortexJobTerminalStatus,
    meta: Meta
  )(implicit ec: ExecutionContext): Future[Unit] =
    for {
      _ <- cleanUp(meta.importedFilePath)
      model <- cvModelCommonService.loadModelMandatory(meta.modelId)
      _ <- cvModelCommonService.assertModelStatus(model, CVModelStatus.Saving).toFuture
      _ <- lastStatus match {
        case CortexJobStatus.Completed =>
          for {
            _ <- importService.deleteImportedEntityFile(meta.importedFilePath)
            outputPath <- cortexJobService.getJobOutputPath(jobId)
            rawJobResult <- jobMetaService.readRawMeta(jobId, outputPath)
            importResult <- Try(CVModelImportResult.parseFrom(rawJobResult)).toFuture
            _ <- modelDao.update(
              model.id,
              _.copy(
                status = CVModelStatus.Active,
                cortexModelReference = importResult.cvModelReference.map { modelReference =>
                  CortexModelReference(
                    cortexId = modelReference.id,
                    cortexFilePath = modelReference.filePath
                  )
                },
                cortexFeatureExtractorReference = importResult.featureExtractorReference.map { feReference =>
                  CortexModelReference(
                    cortexId = feReference.id,
                    cortexFilePath = feReference.filePath
                  )
                }
              )
            )
          } yield ()
        case CortexJobStatus.Cancelled =>
          cvModelCommonService.updateModelStatus(model.id, CVModelStatus.Cancelled)
        case CortexJobStatus.Failed =>
          cvModelCommonService.updateModelStatus(model.id, CVModelStatus.Error)
      }
    } yield ()

  override protected def handleException(meta: Meta): Future[Unit] =
    for {
      _ <- cleanUp(meta.importedFilePath)
      _ <- cvModelCommonService.updateModelStatus(meta.modelId, CVModelStatus.Error)
    } yield ()

  private def cleanUp(importedFilePath: String): Future[Unit] =
    importService.deleteImportedEntityFile(importedFilePath)

}

object CVModelImportResultHandler {

  case class Meta(modelId: String, importedFilePath: String)

  implicit val CVModelImportResultHandlerMetaFormat: OFormat[Meta] = Json.format[Meta]

}
