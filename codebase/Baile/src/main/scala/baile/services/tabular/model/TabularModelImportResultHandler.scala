package baile.services.tabular.model

import java.util.UUID

import baile.dao.tabular.model.TabularModelDao
import baile.domain.common.CortexModelReference
import baile.domain.job.{ CortexJobStatus, CortexJobTerminalStatus }
import baile.domain.tabular.model.TabularModelStatus
import baile.services.common.MLEntityExportImportService
import baile.services.cortex.job.{ CortexJobService, JobMetaService }
import baile.services.process.JobResultHandler
import baile.services.tabular.model.TabularModelImportResultHandler.Meta
import cortex.api.job.tabular.TabularModelImportResult
import play.api.libs.json.{ Json, OFormat, Reads }
import baile.utils.TryExtensions._

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

class TabularModelImportResultHandler(
  cortexJobService: CortexJobService,
  importService: MLEntityExportImportService,
  tabularModelCommonService: TabularModelCommonService,
  jobMetaService: JobMetaService,
  modelDao: TabularModelDao
) extends JobResultHandler[Meta] {

  override protected val metaReads: Reads[Meta] =
    TabularModelImportResultHandler.TabularModelImportResultHandlerMetaFormat

  override protected def handleResult(
    jobId: UUID,
    lastStatus: CortexJobTerminalStatus,
    meta: Meta
  )(implicit ec: ExecutionContext): Future[Unit] = {
    for {
      _ <- cleanUp(meta.importedFilePath)
      model <- tabularModelCommonService.loadModelMandatory(meta.modelId)
      _ <- tabularModelCommonService.assertModelStatus(model, TabularModelStatus.Saving).toFuture
      _ <- lastStatus match {
        case CortexJobStatus.Completed =>
          for {
            _ <- importService.deleteImportedEntityFile(meta.importedFilePath)
            outputPath <- cortexJobService.getJobOutputPath(jobId)
            rawJobResult <- jobMetaService.readRawMeta(jobId, outputPath)
            importResult <- Try(TabularModelImportResult.parseFrom(rawJobResult)).toFuture
            modelReference = importResult.getTabularModelReference
            _ <- modelDao.update(
              model.id,
              _.copy(
                status = TabularModelStatus.Active,
                cortexModelReference = Some(CortexModelReference(
                  cortexId = modelReference.id,
                  cortexFilePath = modelReference.filePath
                ))
              )
            )
          } yield ()
        case CortexJobStatus.Cancelled =>
          tabularModelCommonService.updateModelStatus(model.id, TabularModelStatus.Cancelled)
        case CortexJobStatus.Failed =>
          tabularModelCommonService.updateModelStatus(model.id, TabularModelStatus.Error)
      }
    } yield ()
  }

  override protected def handleException(meta: Meta): Future[Unit] = {
    for {
      _ <- cleanUp(meta.importedFilePath)
      _ <- tabularModelCommonService.updateModelStatus(meta.modelId, TabularModelStatus.Error)
    } yield ()
  }

  private def cleanUp(importedFilePath: String): Future[Unit] =
    importService.deleteImportedEntityFile(importedFilePath)

}

object TabularModelImportResultHandler {

  case class Meta(modelId: String, importedFilePath: String)

  implicit val TabularModelImportResultHandlerMetaFormat: OFormat[Meta] = Json.format[Meta]

}
