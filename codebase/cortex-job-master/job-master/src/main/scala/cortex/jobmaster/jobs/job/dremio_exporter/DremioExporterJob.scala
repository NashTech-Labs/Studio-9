package cortex.jobmaster.jobs.job.dremio_exporter

import cortex.TaskResult
import cortex.jobmaster.jobs.TaskIdGenerator
import cortex.jobmaster.jobs.job.tabular.TableExporterJob
import cortex.scheduler.TaskScheduler
import cortex.task.tabular_data.Table
import cortex.task.transform.common.{ CSVParams, Column, TableFileType }
import cortex.task.transform.exporter.dremio.DremioExporterModule
import cortex.task.transform.exporter.dremio.DremioExporterParams.DremioExporterTaskParams
import cortex.task.{ StorageAccessParams, TabularAccessParams }

import scala.concurrent.{ ExecutionContext, Future }

class DremioExporterJob(
    scheduler:               TaskScheduler,
    dremioExporterModule:    DremioExporterModule,
    dremioAccessParams:      TabularAccessParams.DremioAccessParams,
    s3AccessParams:          StorageAccessParams.S3AccessParams,
    dremioExporterJobConfig: DremioExporterJobConfig
)(implicit val context: ExecutionContext) extends TableExporterJob with TaskIdGenerator {

  val chunksize: Int = dremioExporterJobConfig.chunksize

  override def exportToTable(
    jobId:     String,
    table:     Table,
    srcPath:   String,
    fileType:  TableFileType,
    columns:   Seq[Column],
    csvParams: Option[CSVParams] = None
  ): Future[TaskResult.Empty] = {

    val task = dremioExporterModule.transformTask(
      id       = genTaskId(jobId),
      jobId    = jobId,
      taskPath = s"$jobId/export_to_dremio",
      params   = DremioExporterTaskParams(
        dremioAccessParams = dremioAccessParams,
        table              = table,
        s3AccessParams     = s3AccessParams,
        s3SrcPath          = srcPath,
        fileType           = fileType,
        columns            = columns,
        chunksize          = chunksize,
        csvParams          = csvParams
      ),
      cpus     = dremioExporterJobConfig.cpus,
      memory   = dremioExporterJobConfig.taskMemoryLimit
    )
    scheduler.submitTask(task)
  }
}
