package cortex.jobmaster.orion.service.domain.column_statistics

import cortex.api.job.JobRequest
import cortex.api.job.JobType.TabularColumnStatistics
import cortex.api.job.table.TabularColumnStatisticsRequest
import cortex.common.logging.JMLoggerFactory
import cortex.jobmaster.jobs.TaskIdGenerator
import cortex.jobmaster.jobs.job.columns_statistics.ColumnStatisticsJob
import cortex.jobmaster.modules.SettingsModule
import cortex.jobmaster.orion.service.domain.JobRequestPartialHandler
import cortex.jobmaster.orion.service.domain.JobRequestPartialHandler.{ JobId, JobResult }
import cortex.scheduler.TaskScheduler
import cortex.task.StorageAccessParams.S3AccessParams
import cortex.task.column_statistics.ColumnStatisticsModule
import cortex.task.TabularAccessParams.{ DremioAccessParams, RedshiftAccessParams }

import scala.concurrent.ExecutionContext

class ColumnStatisticsService(
    job: ColumnStatisticsJob
)(implicit val context: ExecutionContext) extends JobRequestPartialHandler with TaskIdGenerator {
  override def handlePartial: PartialFunction[(JobId, JobRequest), JobResult] = {
    case (jobId, jobReq) if jobReq.`type` == TabularColumnStatistics =>
      val importRequest = TabularColumnStatisticsRequest.parseFrom(jobReq.payload.toByteArray)
      job.calculateColumnStatistics(jobId, importRequest)
  }
}

object ColumnStatisticsService {

  def apply(
    scheduler:      TaskScheduler,
    s3AccessParams: S3AccessParams,
    settings:       SettingsModule
  )(implicit executionContext: ExecutionContext, loggerFactory: JMLoggerFactory): ColumnStatisticsService = {
    import settings.baseRedshiftConfig

    val redshiftAccessParams = RedshiftAccessParams(
      hostname  = baseRedshiftConfig.host,
      port      = baseRedshiftConfig.port,
      username  = baseRedshiftConfig.username,
      password  = baseRedshiftConfig.password,
      database  = baseRedshiftConfig.database,
      s3IamRole = baseRedshiftConfig.s3IAMRole
    )

    val columnStatisticsModule = new ColumnStatisticsModule

    val columnStatisticsJob = new ColumnStatisticsJob(
      scheduler                 = scheduler,
      module                    = columnStatisticsModule,
      tabularAccessParams       = redshiftAccessParams,
      columnStatisticsJobConfig = settings.columnStatisticsConfig
    )

    new ColumnStatisticsService(columnStatisticsJob)
  }

}
