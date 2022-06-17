package cortex.jobmaster.jobs.job.dremio_importer

import cortex.jobmaster.jobs.TaskIdGenerator
import cortex.jobmaster.jobs.job.tabular.TableImporterJob
import cortex.scheduler.TaskScheduler
import cortex.task.{ StorageAccessParams, TabularAccessParams }
import cortex.task.tabular_data.{ Table, TableImportResult }
import cortex.task.transform.common.CSVParams
import cortex.task.transform.importer.dremio.DremioImporterModule
import cortex.task.transform.importer.dremio.DremioImporterParams.DremioImporterTaskParams

import scala.concurrent.{ ExecutionContext, Future }

class DremioImporterJob(
    scheduler:               TaskScheduler,
    dremioImporterModule:    DremioImporterModule,
    dremioAccessParams:      TabularAccessParams.DremioAccessParams,
    s3AccessParams:          StorageAccessParams.S3AccessParams,
    dremioImporterJobConfig: DremioImporterJobConfig
)(implicit val context: ExecutionContext) extends TableImporterJob with TaskIdGenerator {

  override def importFromTable(
    jobId:     String,
    table:     Table,
    destPath:  String,
    csvParams: Option[CSVParams]
  ): Future[TableImportResult] = {
    val task = dremioImporterModule.transformTask(
      id       = genTaskId(jobId),
      jobId    = jobId,
      taskPath = s"$jobId/import_from_dremio",
      params   = DremioImporterTaskParams(
        dremioAccessParams = dremioAccessParams,
        table              = table,
        s3AccessParams     = s3AccessParams,
        s3DestPath         = destPath,
        csvParams          = csvParams
      ),
      cpus     = dremioImporterJobConfig.cpus,
      memory   = dremioImporterJobConfig.taskMemoryLimit
    )
    scheduler.submitTask(task)
  }
}

