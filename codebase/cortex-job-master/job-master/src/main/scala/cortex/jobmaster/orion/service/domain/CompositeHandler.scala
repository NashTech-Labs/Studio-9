package cortex.jobmaster.orion.service.domain

import cortex.api.job.JobRequest
import cortex.common.Logging
import cortex.common.logging.JMLoggerFactory
import cortex.io.S3Client
import cortex.jobmaster.modules.SettingsModule
import cortex.jobmaster.orion.service.domain.JobRequestPartialHandler._
import cortex.jobmaster.orion.service.domain.column_statistics.ColumnStatisticsService
import cortex.jobmaster.orion.service.domain.computer_vision.ComputerVisionService
import cortex.jobmaster.orion.service.domain.dataset.DatasetService
import cortex.jobmaster.orion.service.domain.online_prediction.OnlinePredictionService
import cortex.jobmaster.orion.service.domain.table_uploading.TableUploadingService
import cortex.jobmaster.orion.service.io.S3ParamResultStorageFactory
import cortex.scheduler.TaskScheduler
import cortex.task.StorageAccessParams.S3AccessParams
import cortex.task.TabularAccessParams

import scala.concurrent.ExecutionContext

class CompositeHandler(handlers: JobRequestPartialHandler*)(implicit val loggerFactory: JMLoggerFactory)
  extends JobRequestHandler with Logging {
  private val jobHandler = combineHandlers(handlers.map(_.handlePartial))

  def handleJobRequest(jobRequest: (JobId, JobRequest)): JobResult = {
    log.debug(s"start handling job request ${jobRequest._1}")
    jobHandler.apply(jobRequest)
  }

  private def combineHandlers(handlers: Seq[PartialFunction[(JobId, JobRequest), JobResult]]) = {
    handlers reduceLeft (_ orElse _)
  }
}

object CompositeHandler {
  def apply(
    s3client:       S3Client,
    scheduler:      TaskScheduler,
    storageFactory: S3ParamResultStorageFactory,
    settings:       SettingsModule
  )(implicit executionContext: ExecutionContext, loggerFactory: JMLoggerFactory): CompositeHandler = {
    val s3AccessParams: S3AccessParams = S3AccessParams(
      settings.baseS3Config.baseBucket,
      settings.baseS3Config.accessKey,
      settings.baseS3Config.secretKey,
      settings.baseS3Config.region
    )

    val redshiftAccessParams = TabularAccessParams.RedshiftAccessParams(
      hostname  = settings.baseRedshiftConfig.host,
      port      = settings.baseRedshiftConfig.port,
      username  = settings.baseRedshiftConfig.username,
      password  = settings.baseRedshiftConfig.password,
      database  = settings.baseRedshiftConfig.database,
      s3IamRole = settings.baseRedshiftConfig.s3IAMRole
    )

    val handlers = Seq(
      ComputerVisionService(
        scheduler            = scheduler,
        s3AccessParams       = s3AccessParams,
        redshiftAccessParams = redshiftAccessParams,
        settings             = settings
      ),
      TabularDataService(
        scheduler            = scheduler,
        s3AccessParams       = s3AccessParams,
        redshiftAccessParams = redshiftAccessParams,
        s3client             = s3client,
        settings             = settings
      ),
      ImageUploadingService(
        scheduler      = scheduler,
        s3AccessParams = s3AccessParams,
        settings       = settings
      ),
      VideoUploadingService(
        scheduler      = scheduler,
        s3AccessParams = s3AccessParams,
        settings       = settings
      ),
      OnlinePredictionService(
        scheduler      = scheduler,
        s3AccessParams = s3AccessParams,
        storageFactory = storageFactory,
        settings       = settings
      ),
      TableUploadingService(
        scheduler      = scheduler,
        s3AccessParams = s3AccessParams,
        settings       = settings
      ),
      DataAugmentationService(
        scheduler      = scheduler,
        s3AccessParams = s3AccessParams,
        settings       = settings
      ),
      ProjectPackagerService(
        scheduler      = scheduler,
        s3AccessParams = s3AccessParams,
        settings       = settings
      ),
      ColumnStatisticsService(
        scheduler      = scheduler,
        s3AccessParams = s3AccessParams,
        settings       = settings
      ),
      PipelineService(
        scheduler      = scheduler,
        settings       = settings,
        s3Client       = s3client,
        s3AccessParams = s3AccessParams
      ),
      DatasetService(
        scheduler      = scheduler,
        s3AccessParams = s3AccessParams,
        settings       = settings
      )
    )

    new CompositeHandler(handlers: _*)
  }
}
