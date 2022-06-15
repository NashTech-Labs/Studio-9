package cortex.jobmaster.commands

import akka.actor.ActorSystem
import cortex.common.logging.JMLoggerFactory
import cortex.io.S3Client
import cortex.jobmaster.modules.SettingsModule
import cortex.jobmaster.orion.service.domain.CompositeHandler
import cortex.jobmaster.orion.service.io.{ S3ParamResultStorageFactory, S3StorageCleaner }
import cortex.jobmaster.orion.service.{ OrionJobServiceAdapter, RabbitMqService }
import cortex.rpc.{ S3TaskRPC, TaskRPC }
import cortex.scheduler.MesosTaskScheduler

import scala.concurrent.duration._
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global

class ServiceCliCommand(
    jobId:       String,
    mesosMaster: String,
    settings:    SettingsModule
)(implicit loggerFactory: JMLoggerFactory) extends CliCommand {
  import settings.baseS3Config
  import settings.taskRpcPath
  import settings.jobsPath
  import settings.modelsPath
  import settings.jmConf
  import settings.akkaConfig
  import settings.heartbeatInterval

  private val log = loggerFactory.getLogger(this.getClass.toString)
  private val s3client = new S3Client(baseS3Config.accessKey, baseS3Config.secretKey, baseS3Config.region)
  private val taskRPC = new S3TaskRPC(baseS3Config.baseBucket, taskRpcPath, s3client)
  private val scheduler = createScheduler(mesosMaster, taskRPC)
  private val storageFactory = new S3ParamResultStorageFactory(s3client, baseS3Config.baseBucket, jobsPath)

  override def execute(): Unit = {
    log.info("Starting Cortex Job Master")
    val compositeHandler = CompositeHandler(s3client, scheduler, storageFactory, settings)
    val storageCleaner = new S3StorageCleaner(s3client, baseS3Config.baseBucket)
    val system = ActorSystem("rabbit-mq-service-system", akkaConfig)
    val rabbitMqService = new RabbitMqService(system)
    val service = new OrionJobServiceAdapter(
      jobId              = jobId,
      rabbitMqService    = rabbitMqService,
      jobRequestHandler  = compositeHandler,
      storageFactory     = storageFactory,
      taskScheduler      = scheduler,
      storageCleaner     = storageCleaner,
      resourcesBasePaths = Seq(taskRpcPath, jobsPath, modelsPath),
      heartbeatInterval  = heartbeatInterval
    )

    scala.sys.addShutdownHook {
      rabbitMqService.stop()
      system.terminate()
      Await.result(system.whenTerminated, 1 minute)
    }

    service.start()
  }

  private def createScheduler(mesosMaster: String, taskRPC: TaskRPC)(implicit loggerFactory: JMLoggerFactory) = {
    val scheduler = new MesosTaskScheduler(
      mesosMaster,
      taskRPC,
      dockerImageVersion  = jmConf.tasksVersion,
      dockerImageRegistry = jmConf.tasksRegistry
    )
    scheduler.start()
    scheduler
  }
}
