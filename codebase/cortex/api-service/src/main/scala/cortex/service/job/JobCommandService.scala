package cortex.service.job

import java.util.UUID

import akka.actor.Props
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.pattern.pipe
import akka.util.Timeout
import com.spingo.op_rabbit.Message.Ack
import cortex.api.job.message._
import cortex.common.json4s.CortexJson4sSupport
import cortex.common.service._
import cortex.domain.rest.job.TaskTimeInfoContract
import cortex.domain.service.job._
import cortex.domain.service.{ CreateEntity, DeleteEntity }
import orion.ipc.rabbitmq.MlJobTopology._
import orion.ipc.rabbitmq.{ MlJobTopology, RabbitMqRpcClientSupport }

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{ Failure, Success }
import orion.ipc.common._

object JobCommandService extends NamedActor {
  override final val Name = "job-command-service"

  def props(): Props = {
    Props(new JobCommandService())
  }
}

class JobCommandService extends Service
    with UUIDSupport
    with DateSupport
    with CortexJson4sSupport
    with RabbitMqRpcClientSupport
    with HttpClientSupport {

  log.debug(s"JobCommandService is starting... - ${self.path.name}")

  implicit val ec = context.dispatcher
  implicit val timeout = Timeout(30 seconds)

  val ariesSettings = AriesSettings(context.system)
  val ariesBaseUrl = ariesSettings.baseUrl
  val ariesCommandCredentials = BasicHttpCredentials(ariesSettings.commandCredentials.username, ariesSettings.commandCredentials.password)
  val ariesRequestRetryCount = ariesSettings.requestRetryCount

  override def preStart(): Unit = {
    subscribe[JobMessage](MlJobTopology.JobStatusQueue)
  }

  def receive: Receive = {

    case CreateEntity(job: SubmitJobData) => {
      def createJob(job: SubmitJobData): Future[JobEntity] = {
        val jobId = job.id.getOrElse(randomUUID)
        val jobEntity =
          CreateJobData(
            id          = job.id.getOrElse(randomUUID),
            submittedAt = currentDate,
            owner       = job.owner,
            jobType     = job.jobType,
            inputPath   = job.inputPath,
            status      = JobStatus.Submitted
          )

        log.info("[ JobId: {} ] - [Create Job] - Sending create request to Aries REST API.", jobId)
        withRetry(ariesRequestRetryCount)(post(s"$ariesBaseUrl/jobs", jobEntity, Some(ariesCommandCredentials)).unwrapTo[JobEntity]) andThen {
          case Success(_) => log.info("[ JobId: {} ] - [Create Job] - Job creation succeeded.", jobId)
          case Failure(e) => log.error("[ JobId: {} ] - [Create Job] - Failed to create Job [{}] with error: {}", jobId, jobEntity, e)
        }
      }

      def submitJob(job: JobEntity): Future[Ack] = {
        val jobId = job.id
        val jobType = job.jobType
        val inputPath = job.inputPath
        val routingKey = NewJobRoutingKeyTemplate.format(jobId)
        val msg = JobMessage(JobMessageMeta(jobId, Some(jobType)), SubmitJob(inputPath))

        log.info("[ JobId: {} ] - [Submit Job] - Publishing SubmitJob msg to RabbitMq.", jobId)
        sendMessageToExchangeWithConfirmation(msg, GatewayExchange, routingKey) andThen {
          case Success(_) => log.info("[ JobId: {} ] - [Submit Job] - Msg publishing succeeded.", jobId)
          case Failure(e) => log.error("[ JobId: {} ] - [Submit Job] - Failed to publish msg [{}] with error: [{}]", jobId, msg, e)
        }
      }

      val result =
        for {
          jobEntity <- createJob(job)
          _ <- submitJob(jobEntity)
        } yield jobEntity

      result pipeTo sender
    }

    case DeleteEntity(jobId) => {
      def remove(jobId: UUID): Future[Option[JobEntity]] = {
        log.info("[ JobId: {} ] - [Delete Job] - Sending delete request to Aries REST API.", jobId)
        withRetry(ariesRequestRetryCount)(delete(s"$ariesBaseUrl/jobs/$jobId", Some(ariesCommandCredentials)).unwrapToOption[JobEntity]) andThen {
          case Success(Some(_)) => log.info("[ JobId: {} ] - [Delete Job] - Job deletion succeeded.", jobId)
          case Success(None)    => log.warning("[ JobId: {} ] - [Delete Job] - Try to delete Job but it was not found.", jobId)
          case Failure(e)       => log.error("[ JobId: {} ] - [Delete Job] - Failed to delete Job with error: [{}]", jobId, e)
        }
      }

      def cancelJob(jobId: UUID): Future[Ack] = {
        val routingKey = CancelJobRoutingKeyTemplate.format(jobId)
        log.info("[ JobId: {} ] - [Cancel Job] - Publishing CancelJob msg to RabbitMq.", jobId)
        sendMessageToExchangeWithConfirmation(JobMessage(JobMessageMeta(jobId, None), CancelJob), GatewayExchange, routingKey) andThen {
          case Success(_) => log.info("[ JobId: {} ] - [Cancel Job] - Msg publishing succeeded.", jobId)
          case Failure(e) => log.error("[ JobId: {} ] - [Cancel Job] - Msg publishing failed with error: [{}]", jobId, e)
        }
      }

      // TODO: refactor this using monad transformers
      val result: Future[Option[JobEntity]] =
        remove(jobId).flatMap {
          case job @ Some(_) => cancelJob(jobId).map(_ => job)
          case None          => Future.successful(None)
        }

      result pipeTo sender
    }

    case JobMessage(JobMessageMeta(jobId, _), JobStarted(date)) => {
      // TODO: should we post an initial Heartbeat here as well?
      val updateData = UpdateJobData(
        status   = Some(JobStatus.Running),
        timeInfo = Some(UpdateTimeInfo(startedAt = Some(date)))
      )

      log.info("[ JobId: {} ] - [Update Job Start] - Sending update request to Aries REST API.", jobId)
      withRetry(ariesRequestRetryCount)(updateJob(jobId, updateData)) andThen {
        case Success(Some(_)) => log.info("[ JobId: {} ] - [Update Job Start] - Update job succeeded.", jobId)
        case Success(None)    => log.warning("[ JobId: {} ] - [Update Job Start] - Try to update Job but it was not found", jobId)
        case Failure(e)       => log.error("[ JobId: {} ] - [Update Job Start] - Failed to update Job data [{}] with error: [{}]", jobId, updateData, e)
      }
    }

    case JobMessage(JobMessageMeta(jobId, _), Heartbeat(date, currentProgress, estimatedTimeRemaining)) => {
      def addHeartBeat(heartbeat: HeartbeatData): Future[HeartbeatData] = {
        log.info("[ JobId: {} ] - [Add Heartbeat] - Sending create request to Aries REST API.", jobId)
        withRetry(ariesRequestRetryCount)(post(s"$ariesBaseUrl/heartbeats", heartbeat, Some(ariesCommandCredentials)).unwrapTo[HeartbeatData]) andThen {
          case Success(_) => log.info("[ JobId: {} ] - [Add Heartbeat] - Heartbeat adding succeeded.", jobId)
          case Failure(e) => log.error("[ JobId: {} ] - [Add Heartbeat] - Failed to add Heartbeat [{}] with error: [{}].", jobId, heartbeat, e)
        }
      }

      addHeartBeat(HeartbeatData(jobId, date, currentProgress, estimatedTimeRemaining))
    }

    case JobMessage(JobMessageMeta(jobId, _), result: JobResult) => {
      // TODO: should we post a final Heartbeat here as well?

      val updateData =
        result match {
          case JobResultSuccess(completedAt, tasksTimeInfo, tasksQueuedTime, outputPath) =>
            UpdateJobData(
              status          = Some(JobStatus.Completed),
              outputPath      = Some(outputPath),
              timeInfo        = Some(UpdateTimeInfo(completedAt = Some(completedAt))),
              tasksTimeInfo   = Some(tasksTimeInfo),
              tasksQueuedTime = Some(tasksQueuedTime)
            )
          case JobResultFailure(completedAt, errorCode, errorMessage, errorDetails) =>
            UpdateJobData(
              status             = Some(JobStatus.Failed),
              timeInfo           = Some(UpdateTimeInfo(completedAt = Some(completedAt))),
              cortexErrorDetails = Some(CortexErrorDetails(errorCode, errorMessage, errorDetails))
            )
        }

      log.info("[ JobId: {} ] - [Update Job Result] - Sending update request to Aries REST API.", jobId)
      withRetry(ariesRequestRetryCount)(updateJob(jobId, updateData)) andThen {
        case Success(Some(_)) => log.info("[ JobId: {} ] - [Update Job Result] - Update job succeeded.", jobId)
        case Success(None)    => log.warning("[ JobId: {} ] - [Update Job Result] - Try to update Job but it was not found", jobId)
        case Failure(e)       => log.error("[ JobId: {} ] - [Update Job Result] - Failed to update Job data [{}] with error: [{}]", jobId, updateData, e)
      }
    }
  }

  def updateJob(jobId: UUID, updateData: UpdateJobData): Future[Option[JobEntity]] = {
    put(s"$ariesBaseUrl/jobs/$jobId", updateData, Some(ariesCommandCredentials)).unwrapToOption[JobEntity]
  }

}
