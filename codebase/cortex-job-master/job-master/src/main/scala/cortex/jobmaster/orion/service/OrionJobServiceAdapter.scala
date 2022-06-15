package cortex.jobmaster.orion.service

import java.util.concurrent.atomic.AtomicBoolean
import java.util.{ Date, UUID }

import com.trueaccord.scalapb.GeneratedMessage
import cortex.api.job.JobRequest
import cortex.api.job.message._
import cortex.common.Logging
import cortex.common.logging.JMLoggerFactory
import cortex.jobmaster.jobs.time.JobTimeInfo
import cortex.jobmaster.orion.service.domain.JobRequestHandler
import cortex.jobmaster.orion.service.io.{ BaseStorageFactory, StorageCleaner }
import cortex.scheduler._
import org.apache.commons.lang3.exception.ExceptionUtils
import orion.ipc.rabbitmq.MlJobTopology.{ GatewayExchange, JobMasterInQueueTemplate, JobMasterOutRoutingKeyTemplate }

import scala.concurrent.ExecutionContext
import scala.util.{ Failure, Success, Try }

class OrionJobServiceAdapter(
    jobId:              String,
    rabbitMqService:    RabbitMqService,
    jobRequestHandler:  JobRequestHandler,
    storageFactory:     BaseStorageFactory,
    taskScheduler:      TaskScheduler,
    storageCleaner:     StorageCleaner,
    resourcesBasePaths: Seq[String],
    heartbeatInterval:  Int
)(implicit val ec: ExecutionContext, val loggerFactory: JMLoggerFactory)
  extends Logging {

  /** Communication endpoints with messaging service */
  private val jobInQueue = JobMasterInQueueTemplate.format(jobId)
  private val jobOutRoutingKey = JobMasterOutRoutingKeyTemplate.format(jobId)
  private val outGateway = GatewayExchange
  protected val sendingHeartbeats = new AtomicBoolean(true)
  private var isCanceled = false

  def start(): Unit = {
    rabbitMqService.subscribe(jobInQueue, handle)
  }

  protected def handle(jobMessage: JobMessage): Unit = {

    log.info(s"started handling job message")

    jobMessage.payload match {

      case SubmitJob(path) =>
        startJob(jobMessage.meta.jobId, path)

      case CancelJob =>
        log.info(s"cancelling job")
        cancelJob(jobMessage.meta.jobId)

      case _ => log.error(s"cannot handle message: ${jobMessage.payload}")
    }
  }

  protected def startJob(jobId: UUID, path: String): Unit = {
    // first heartbeat to make orion aware that jm has started
    startHeartbeat(jobId, heartbeatInterval)

    Try {
      log.info(s"retrieving job request from path: $path")
      val storage = storageFactory.createParamResultStorageReader[JobRequest]()
      val jobRequest = storage.get(path)
      log.info(s"finished retrieving job request")

      jobRequestHandler.handleJobRequest((jobId.toString, jobRequest))
    } match {
      case Success(jobResult) =>
        jobResult onComplete {
          case Success((resultMsg, jobTimeInfo: JobTimeInfo)) =>
            log.info(s"job finished successfully")
            val storage = storageFactory.createParamResultStorageWriter[GeneratedMessage]()
            val path = storage.put(resultMsg, jobId.toString)
            log.info(s"persisted job results in path: $path")

            val msg = JobMessage(
              JobMessageMeta(jobId),
              JobResultSuccess(
                new Date(),
                toTasksTimeInfo(jobTimeInfo.jobTasksTimeInfo),
                jobTimeInfo.jobTasksQueuedTime,
                path
              )
            )

            rabbitMqService.sendMessageToExchange(msg, outGateway, jobOutRoutingKey)
            log.info(s"sent result message [$msg] to exchange")

          case Failure(_: SchedulerStoppedCortexException) =>
            log.info(s"job has been stopped it's supposed to be due to the fact that the job had been canceled")

          case Failure(userCortexException: UserCortexException) =>
            val errorCode = userCortexException.errorCode
            val errorMessage = userCortexException.errorMessage
            val stackTrace = userCortexException.stackTrace
            handleFailure(jobId, path, errorCode, errorMessage, stackTrace)

          case Failure(systemCortexException: SystemCortexException) =>
            val errorMessage = systemCortexException.errorMessage
            val stackTrace = systemCortexException.stackTrace
            handleFailure(jobId, path, "sys-error", errorMessage, stackTrace)

          case Failure(e) =>
            handleFailure(jobId, path, e)
        }
      case Failure(e) =>
        handleFailure(jobId, path, e)
    }
  }

  //consider using actor model
  protected def startHeartbeat(jobId: UUID, heartbeatInterval: Int): Unit = {
    val heartbeatThread = new Thread(new Runnable {
      override def run(): Unit = {
        while (sendingHeartbeats.get()) {
          val heartbeat = JobMessage(
            meta    = JobMessageMeta(jobId),
            payload = Heartbeat(
              date                   = new Date(),
              currentProgress        = 0D,
              estimatedTimeRemaining = None
            )
          )
          log.info(s"sent heartbeat")
          rabbitMqService.sendMessageToExchange(heartbeat, outGateway, jobOutRoutingKey)

          Thread.sleep(heartbeatInterval * 1000L)
        }
      }
    })
    heartbeatThread.setDaemon(true)
    heartbeatThread.start()
  }

  protected def handleFailure(
    jobId:        UUID,
    path:         String,
    errorCode:    String,
    errorMessage: String,
    stackTrace:   String
  ): Unit = {
    val errorDetails: Map[String, String] = Map(
      "errorMessage" -> errorMessage,
      "stackTrace" -> stackTrace
    )
    val failureMessage = JobMessage(
      JobMessageMeta(jobId),
      JobResultFailure(
        new Date(),
        errorCode,
        s"[ JobId: ${jobId.toString}] job failed params path: $path",
        errorDetails
      )
    )

    rabbitMqService.sendMessageToExchange(failureMessage, outGateway, jobOutRoutingKey)
    log.info(s"sent failure result message to exchange")
  }
  protected def handleFailure(jobId: UUID, path: String, exception: Throwable): Unit = {
    log.error(s"job failed ${exception.getMessage}")
    log.error(s"job failed ${ExceptionUtils.getStackTrace(exception)}")
    val errorDetails: Map[String, String] = Map(
      "errorMessage" -> exception.getMessage,
      "stackTrace" -> exception.getStackTrace.mkString("\n")
    )
    val msg = JobMessage(
      JobMessageMeta(jobId),
      JobResultFailure(
        completedAt  = new Date(),
        errorCode    = "sys-error",
        errorMessage = s"[ JobId: ${jobId.toString}] job failed params path: $path",
        errorDetails = errorDetails
      )
    )

    rabbitMqService.sendMessageToExchange(msg, outGateway, jobOutRoutingKey)
    log.info(s"sent failure message [$msg] to exchange")
  }

  protected def cancelJob(jobId: UUID): Unit = {
    if (isCanceled) {
      log.warn("Job was already canceled")
    } else {
      taskScheduler.stop()

      //release resources
      resourcesBasePaths.foreach(basePath => {
        val resourcePath = s"$basePath/${jobId.toString}"
        log.info(s"deleting resource path $resourcePath")
        storageCleaner.deleteRecursively(resourcePath)
      })

      sendingHeartbeats.set(false)
      sendReadyForTerminationSignal(jobId)

      isCanceled = true
    }
  }

  protected def sendReadyForTerminationSignal(jobId: UUID): Unit = {
    val jobMessageMeta = JobMessageMeta(jobId)
    val jobMessage = JobMessage(jobMessageMeta, JobMasterAppReadyForTermination)
    rabbitMqService.sendMessageToExchange(jobMessage, outGateway, jobOutRoutingKey)
  }

  private def toTasksTimeInfo(jobTasksTimeInfo: JobTimeInfo.TasksTimeInfo): Seq[TaskTimeInfo] = {
    jobTasksTimeInfo.map(jobTaskTimeInfo => TaskTimeInfo(
      taskName = jobTaskTimeInfo.taskId,
      timeInfo = TimeInfo(
        submittedAt = jobTaskTimeInfo.submittedAt,
        startedAt   = jobTaskTimeInfo.startedAt,
        completedAt = jobTaskTimeInfo.completedAt
      )
    ))
  }
}
