package cortex.jobmaster.jobs.job.analyse_csv

import cortex.TaskTimeInfo
import cortex.api.job.table.{ FileType => UploadingFileType, _ }
import cortex.common.Logging
import cortex.common.future.FutureExtensions._
import cortex.common.logging.JMLoggerFactory
import cortex.jobmaster.jobs.TaskIdGenerator
import cortex.scheduler.TaskScheduler
import cortex.task.analyse_csv.AnalyseCSVModule
import cortex.task.analyse_csv.AnalyseCSVParams.{ AnalyseCSVTaskParams, AnalyseCSVTaskResult }
import cortex.task.column.ColumnMapping
import cortex.task.task_creators.GenericTask
import cortex.task.StorageAccessParams
import cortex.task.transform.common.TableFileType

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

class AnalyseCSVJob(
    scheduler:           TaskScheduler,
    module:              AnalyseCSVModule,
    inputAccessParams:   StorageAccessParams,
    analyseCSVJobConfig: AnalyseCSVJobConfig
)(implicit val context: ExecutionContext, val loggerFactory: JMLoggerFactory) extends TaskIdGenerator with Logging {

  def analyseCSV(jobId: String, params: TableUploadRequest): Future[(Seq[ColumnMapping], Seq[TaskTimeInfo])] =
    for {
      task <- createTask(jobId, params).toFuture
      result <- scheduler.submitTask(task)
    } yield (result.columns, Seq(result.taskTimeInfo))

  private[analyse_csv] def createTask(
    jobId:         String,
    uploadRequest: TableUploadRequest
  ): Try[GenericTask[AnalyseCSVTaskResult, AnalyseCSVTaskParams]] = Try {

    val taskParams = AnalyseCSVTaskParams(
      inputParams = inputAccessParams,
      filePath    = uploadRequest.sourceFilePath,
      fileType    = uploadRequest.fileType match {
        case UploadingFileType.CSV  => TableFileType.CSV
        case UploadingFileType.JSON => TableFileType.JSON
        case UploadingFileType.Unrecognized(v) =>
          throw new RuntimeException(s"Unexpected file type in the input: $v")
      },
      delimiter   = uploadRequest.delimeter,
      nullValue   = uploadRequest.nullValue
    )

    val task = module.transformTask(
      id       = genTaskId(jobId),
      jobId    = jobId,
      taskPath = s"$jobId/analyse_csv",
      params   = taskParams,
      cpus     = analyseCSVJobConfig.cpus,
      memory   = analyseCSVJobConfig.taskMemoryLimit
    )
    task.setAttempts(2)
    task
  }
}
