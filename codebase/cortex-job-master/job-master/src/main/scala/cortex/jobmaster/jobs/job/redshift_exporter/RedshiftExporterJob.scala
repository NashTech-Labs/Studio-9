package cortex.jobmaster.jobs.job.redshift_exporter

import cortex.TaskResult
import cortex.jobmaster.jobs.TaskIdGenerator
import cortex.jobmaster.jobs.job.tabular.TableExporterJob
import cortex.scheduler.TaskScheduler
import cortex.task.tabular_data.Table
import cortex.task.transform.common.{ CSVParams, Column, TableFileType }
import cortex.task.transform.exporter.redshift.RedshiftExporterModule
import cortex.task.transform.exporter.redshift.RedshiftExporterParams.RedshiftExporterTaskParams
import cortex.task.{ StorageAccessParams, TabularAccessParams }

import scala.concurrent.{ ExecutionContext, Future }

class RedshiftExporterJob(
    scheduler:                 TaskScheduler,
    redshiftAccessParams:      TabularAccessParams.RedshiftAccessParams,
    s3AccessParams:            StorageAccessParams.S3AccessParams,
    redshiftExporterModule:    RedshiftExporterModule,
    redshiftExporterJobConfig: RedshiftExporterJobConfig
)(implicit val context: ExecutionContext) extends TableExporterJob with TaskIdGenerator {

  override def exportToTable(
    jobId:     String,
    table:     Table,
    srcPath:   String,
    fileType:  TableFileType,
    columns:   Seq[Column],
    csvParams: Option[CSVParams] = None
  ): Future[TaskResult.Empty] = {
    val task = redshiftExporterModule.transformTask(
      id       = genTaskId(jobId),
      jobId    = jobId,
      taskPath = s"$jobId/redshift_export",
      params   = RedshiftExporterTaskParams(
        redshiftAccessParams = redshiftAccessParams,
        table                = table,
        columns              = columns,
        s3AccessParams       = s3AccessParams,
        s3DataPath           = srcPath
      ),
      cpus     = redshiftExporterJobConfig.cpus,
      memory   = redshiftExporterJobConfig.taskMemoryLimit
    )
    //for now can be only one attempt because second attempt will fail anyway due to tableAlreadyExists exception
    task.setAttempts(1)
    scheduler.submitTask(task)
  }
}

