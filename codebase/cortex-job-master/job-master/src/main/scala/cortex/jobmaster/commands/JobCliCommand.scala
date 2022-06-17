package cortex.jobmaster.commands

import cortex.common.logging.JMLoggerFactory
import cortex.io.S3Client
import cortex.jobmaster.modules.SettingsModule
import cortex.jobmaster.orion.service.CliJobService
import cortex.jobmaster.orion.service.domain.CompositeHandler
import cortex.jobmaster.orion.service.io.S3ParamResultStorageFactory
import cortex.rpc.S3TaskRPC
import cortex.scheduler.LocalTaskScheduler

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration

class JobCliCommand(
    jobId:              String,
    filePath:           String,
    dockerImageVersion: String,
    settings:           SettingsModule
)(implicit loggerFactory: JMLoggerFactory) extends CliCommand {
  import settings.baseS3Config

  private val log = loggerFactory.getLogger(this.getClass.toString)
  private val s3client = new S3Client(baseS3Config.accessKey, baseS3Config.secretKey, baseS3Config.region)
  private val taskRPC = new S3TaskRPC(baseS3Config.baseBucket, settings.taskRpcPath, s3client)
  private val scheduler = new LocalTaskScheduler(taskRPC, dockerImageVersion)
  private val storageFactory = new S3ParamResultStorageFactory(s3client, baseS3Config.baseBucket, settings.jobsPath)

  override def execute(): Unit = {
    log.info("Starting local Cortex Job Master")
    val compositeService = CompositeHandler(s3client, scheduler, storageFactory, settings)
    val service = new CliJobService(jobId, filePath, compositeService, scheduler)

    scala.sys.addShutdownHook {
      service.stop()
    }

    val result = service.start()
    //to hang the main process until task completes or use Ctrl+C to stop the process
    Await.ready(result, Duration.Inf)
  }
}
