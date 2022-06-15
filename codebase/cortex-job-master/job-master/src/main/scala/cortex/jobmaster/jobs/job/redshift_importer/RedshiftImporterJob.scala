package cortex.jobmaster.jobs.job.redshift_importer

import cortex.jobmaster.jobs.TaskIdGenerator
import cortex.jobmaster.jobs.job.tabular.TableImporterJob
import cortex.scheduler.TaskScheduler
import cortex.task.tabular_data.{ Table, TableImportResult }
import cortex.task.transform.common.CSVParams
import cortex.task.transform.importer.redshift.RedshiftImporterModule
import cortex.task.transform.importer.redshift.RedshiftImporterParams._
import cortex.task.{ StorageAccessParams, TabularAccessParams }

import scala.concurrent.{ ExecutionContext, Future }

class RedshiftImporterJob(
    scheduler:                 TaskScheduler,
    redshiftAccessParams:      TabularAccessParams.RedshiftAccessParams,
    s3AccessParams:            StorageAccessParams.S3AccessParams,
    redshiftImporterModule:    RedshiftImporterModule,
    redshiftImporterJobConfig: RedshiftImporterJobConfig
)(implicit val context: ExecutionContext) extends TableImporterJob with TaskIdGenerator {

  override def importFromTable(
    jobId:     String,
    table:     Table,
    destPath:  String,
    csvParams: Option[CSVParams]
  ): Future[TableImportResult] = {
    val task = redshiftImporterModule.transformTask(
      id       = genTaskId(jobId),
      jobId    = jobId,
      taskPath = s"$jobId/redshift_import",
      params   = RedshiftImporterTaskParams(
        redshiftAccessParams = redshiftAccessParams,
        table                = table,
        s3AccessParams       = s3AccessParams,
        outputS3Path         = destPath
      ),
      cpus     = redshiftImporterJobConfig.cpus,
      memory   = redshiftImporterJobConfig.taskMemoryLimit
    )
    scheduler.submitTask(task)
  }
}
