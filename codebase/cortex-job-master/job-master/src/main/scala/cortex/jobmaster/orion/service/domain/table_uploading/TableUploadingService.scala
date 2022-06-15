package cortex.jobmaster.orion.service.domain.table_uploading

import cortex.api.job.JobRequest
import cortex.api.job.JobType.TabularUpload
import cortex.api.job.table.{ Column => CortexColumn, _ }
import cortex.common.logging.JMLoggerFactory
import cortex.jobmaster.jobs.TaskIdGenerator
import cortex.jobmaster.jobs.job.analyse_csv.AnalyseCSVJob
import cortex.jobmaster.jobs.job.redshift_exporter.RedshiftExporterJob
import cortex.jobmaster.jobs.job.tabular.TableExporterJob
import cortex.jobmaster.jobs.time.JobTimeInfo
import cortex.jobmaster.modules.SettingsModule
import cortex.jobmaster.orion.service.domain.{ JobRequestPartialHandler, TableConverters }
import cortex.jobmaster.orion.service.domain.JobRequestPartialHandler.{ JobId, JobResult }
import cortex.scheduler.TaskScheduler
import cortex.task.StorageAccessParams.S3AccessParams
import cortex.task.TabularAccessParams
import cortex.task.analyse_csv.AnalyseCSVModule
import cortex.task.column.{ ColumnDataType, ColumnMapping, ColumnVariableType }
import cortex.task.tabular_data.Table
import cortex.task.transform.common.{ CSVParams, Column, TableFileType }
import cortex.task.transform.exporter.redshift.RedshiftExporterModule
import cortex.{ CortexException, TaskTimeInfo }

import scala.concurrent.{ ExecutionContext, Future }

class TableUploadingService(
    analyseCSVJob: AnalyseCSVJob,
    exporterJob:   TableExporterJob
)(implicit val context: ExecutionContext) extends JobRequestPartialHandler with TaskIdGenerator {
  override def handlePartial: PartialFunction[(JobId, JobRequest), JobResult] = {
    case (jobId, jobReq) if jobReq.`type` == TabularUpload =>
      val importRequest = TableUploadRequest.parseFrom(jobReq.payload.toByteArray)
      analyseAndUploadTable(jobId, importRequest)
  }

  private def analyseAndUploadTable(
    jobId:         JobId,
    importRequest: TableUploadRequest
  ): Future[(TableUploadResponse, JobTimeInfo)] = {

    def analyseColumns: Future[(Seq[ColumnMapping], Seq[TaskTimeInfo])] = {
      if (importRequest.columns.nonEmpty) {
        Future.successful((transformColumns(importRequest.columns), Seq.empty[TaskTimeInfo]))
      } else {
        analyseCSVJob.analyseCSV(jobId, importRequest)
      }
    }

    def exportTable(columns: Seq[ColumnMapping]) = {
      val table = TableConverters.apiDataSourceToTable(importRequest.dataSource.get)

      exporterJob.exportToTable(
        jobId     = jobId,
        table     = Table(
          schema = table.schema,
          name   = table.name
        ),
        srcPath   = importRequest.sourceFilePath,
        columns   = columns.map { columnMapping => Column(columnMapping.name, columnMapping.dataType) },
        fileType  = transformFileType(importRequest.fileType),
        csvParams = Some(CSVParams(
          importRequest.delimeter,
          importRequest.nullValue
        ))
      )
    }

    for {
      (columns, analyseCSVJobTasksTimeInfo) <- analyseColumns
      result <- exportTable(columns)
    } yield {
      (transformResult(columns), JobTimeInfo(analyseCSVJobTasksTimeInfo :+ result.taskTimeInfo))
    }
  }

  private def transformColumns(columns: Seq[ColumnInfo]): Seq[ColumnMapping] = {
    columns map { column =>
      val dataType = TableConverters.apiDataTypeToDomain(column.datatype)
      val variableType = column.variableType match {
        case Some(VariableTypeInfo(x)) => TableConverters.apiVariableTypeToDomain(x)
        case None => dataType match {
          case ColumnDataType.TIMESTAMP | ColumnDataType.BOOLEAN | ColumnDataType.STRING =>
            ColumnVariableType.CATEGORICAL
          case ColumnDataType.INTEGER | ColumnDataType.LONG | ColumnDataType.DOUBLE =>
            ColumnVariableType.CONTINUOUS
        }
      }
      ColumnMapping(
        column.name,
        column.displayName.getOrElse(column.name),
        dataType,
        variableType
      )
    }
  }

  private def transformResult(analysedColumns: Seq[ColumnMapping]): TableUploadResponse = {
    val columns = analysedColumns.map { col =>
      CortexColumn(
        name         = col.name,
        displayName  = col.displayName,
        datatype     = col.dataType match {
          case ColumnDataType.BOOLEAN   => DataType.BOOLEAN
          case ColumnDataType.STRING    => DataType.STRING
          case ColumnDataType.INTEGER   => DataType.INTEGER
          case ColumnDataType.LONG      => DataType.LONG
          case ColumnDataType.DOUBLE    => DataType.DOUBLE
          case ColumnDataType.TIMESTAMP => DataType.TIMESTAMP
        },
        variableType = col.variableType match {
          case ColumnVariableType.CATEGORICAL => VariableType.CATEGORICAL
          case ColumnVariableType.CONTINUOUS  => VariableType.CONTINUOUS
        }
      )
    }

    TableUploadResponse(
      columns = columns
    )
  }

  private def transformFileType(fileType: FileType) = {
    fileType match {
      case FileType.CSV  => TableFileType.CSV
      case FileType.JSON => TableFileType.JSON
      case FileType.Unrecognized(unrecognized) =>
        throw new RuntimeException(s"Invalid file type $unrecognized")
    }
  }

}

object TableUploadingService {

  def apply(
    scheduler:      TaskScheduler,
    s3AccessParams: S3AccessParams,
    settings:       SettingsModule
  )(implicit executionContext: ExecutionContext, loggerFactory: JMLoggerFactory): TableUploadingService = {
    import settings.baseRedshiftConfig

    val redshiftAccessParams = TabularAccessParams.RedshiftAccessParams(
      hostname  = baseRedshiftConfig.host,
      port      = baseRedshiftConfig.port,
      username  = baseRedshiftConfig.username,
      password  = baseRedshiftConfig.password,
      database  = baseRedshiftConfig.database,
      s3IamRole = baseRedshiftConfig.s3IAMRole
    )

    val analyseCSVModule = new AnalyseCSVModule

    val redshiftExporterModule = new RedshiftExporterModule()
    val exporterJob = new RedshiftExporterJob(
      scheduler                 = scheduler,
      redshiftAccessParams      = redshiftAccessParams,
      s3AccessParams            = s3AccessParams,
      redshiftExporterModule    = redshiftExporterModule,
      redshiftExporterJobConfig = settings.redshiftExporterConfig
    )

    val analyseCSVJob = new AnalyseCSVJob(
      scheduler           = scheduler,
      module              = analyseCSVModule,
      inputAccessParams   = s3AccessParams,
      analyseCSVJobConfig = settings.analyseCSVConfig
    )

    new TableUploadingService(
      analyseCSVJob = analyseCSVJob,
      exporterJob   = exporterJob
    )
  }
}
