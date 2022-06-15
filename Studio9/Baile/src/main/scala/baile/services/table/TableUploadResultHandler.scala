package baile.services.table

import java.util.UUID

import baile.dao.table.TableDao
import baile.daocommons.WithId
import baile.domain.job.{ CortexJobStatus, CortexJobTerminalStatus }
import baile.domain.table._
import baile.services.common.FileUploadService
import baile.services.cortex.job.{ CortexJobService, JobMetaService }
import baile.services.process.JobResultHandler
import baile.services.table.TableUploadResultHandler.Meta
import baile.utils.TryExtensions._
import cortex.api.job.table.{ TableUploadResponse, Column => CortexColumn }
import cortex.api.job.table.{ DataType, VariableType }
import play.api.libs.json.{ Json, OFormat, Reads }

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

class TableUploadResultHandler(
  tableDao: TableDao,
  cortexJobService: CortexJobService,
  fileUploadService: FileUploadService,
  jobMetaService: JobMetaService,
  tableService: TableService
) extends JobResultHandler[Meta] {

  override protected val metaReads: Reads[Meta] = TableUploadResultHandler.TableUploadResultHandlerMetaFormat

  override protected def handleResult(
    jobId: UUID,
    lastStatus: CortexJobTerminalStatus,
    meta: Meta
  )(implicit ec: ExecutionContext): Future[Unit] = {

    lastStatus match {
      case CortexJobStatus.Completed =>
        for {
          _ <- cleanUp(meta.uploadedFilePath)
          outputPath <- cortexJobService.getJobOutputPath(jobId)
          rawJobResult <- jobMetaService.readRawMeta(jobId, outputPath)
          uploadResult <- Try(TableUploadResponse.parseFrom(rawJobResult)).toFuture
          columns <- Try.sequence(uploadResult.columns.map(buildColumn)).toFuture
          _ <- tableDao.update(
            meta.tableId,
            _.copy(
              columns = columns,
              status = TableStatus.Active
            )
          )
          _ <- tableService.calculateColumnStatistics(meta.tableId, None, meta.userId)
        } yield ()
      case CortexJobStatus.Cancelled | CortexJobStatus.Failed =>
        handleException(meta)
    }

  }

  override def handleException(meta: Meta): Future[Unit] =
    for {
      _ <- cleanUp(meta.uploadedFilePath)
      _ <- failTableAndStatistics(meta.tableId)
    } yield ()

  private def failTableAndStatistics(tableId: String): Future[Option[WithId[Table]]] =
    tableDao.update(tableId, _.copy(status = TableStatus.Error, tableStatisticsStatus = TableStatisticsStatus.Error))

  private def cleanUp(uploadedFilePath: String): Future[Unit] =
    fileUploadService.deleteUploadedFile(uploadedFilePath)

  private def buildColumn(column: CortexColumn): Try[Column] = Try {
    val dataType = column.datatype match {
      case DataType.BOOLEAN => ColumnDataType.Boolean
      case DataType.STRING => ColumnDataType.String
      case DataType.TIMESTAMP => ColumnDataType.Timestamp
      case DataType.DOUBLE => ColumnDataType.Double
      case DataType.INTEGER => ColumnDataType.Integer
      case DataType.LONG => ColumnDataType.Long
      case DataType.Unrecognized(dataType) => throw new RuntimeException(s"Invalid source type $dataType")
    }
    Column(
      name = column.name,
      displayName = column.displayName,
      dataType = dataType,
      variableType = column.variableType match {
        case VariableType.CATEGORICAL => ColumnVariableType.Categorical
        case VariableType.CONTINUOUS => ColumnVariableType.Continuous
        case VariableType.Unrecognized(variableType) =>
          throw new RuntimeException(s"Invalid variable type $variableType")
      },
      align = tableService.getColumnAlignment(dataType),
      statistics = None
    )
  }

}

object TableUploadResultHandler {

  case class Meta(tableId: String, uploadedFilePath: String, userId: UUID)

  implicit val TableUploadResultHandlerMetaFormat: OFormat[Meta] = Json.format[Meta]

}
