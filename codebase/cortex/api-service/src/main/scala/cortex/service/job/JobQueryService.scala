package cortex.service.job

import java.util.UUID

import akka.actor.Props
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.pattern.pipe
import cortex.common.json4s.CortexJson4sSupport
import cortex.common.service._
import cortex.domain.service.job._
import cortex.domain.service.{ ListEntities, RetrieveEntity }

import scala.concurrent.Future
import scala.util.{ Failure, Success }
import orion.ipc.common._

object JobQueryService extends NamedActor {

  override final val Name = "job-query-service"

  def props(): Props = {
    Props(new JobQueryService())
  }

}

class JobQueryService extends Service
    with UUIDSupport
    with DateSupport
    with CortexJson4sSupport
    with HttpClientSupport {

  log.debug(s"JobQueryService is starting... - ${self.path.name}")

  implicit val ec = context.dispatcher

  val ariesSettings = AriesSettings(context.system)
  val ariesBaseUrl = ariesSettings.baseUrl
  val ariesQueryCredentials = BasicHttpCredentials(ariesSettings.searchCredentials.username, ariesSettings.searchCredentials.password)
  val ariesRequestRetryCount = ariesSettings.requestRetryCount

  def receive: Receive = {

    case RetrieveEntity(id) => {
      getJob(id) pipeTo sender
    }

    case ListEntities => {
      def list(): Future[Seq[JobEntity]] = {
        log.info("[List Jobs] - Sending list request to Aries REST API.")
        withRetry(ariesRequestRetryCount)(get(s"$ariesBaseUrl/jobs", Some(ariesQueryCredentials)).unwrapTo[Seq[JobEntity]]) andThen {
          case Success(_) => log.info("[List Jobs] - Job listing succeeded")
          case Failure(e) => log.error("[List Jobs] - Failed to list Jobs with error: [{}]", e)
        }
      }

      list() pipeTo sender
    }

    case GetJobStatus(jobId) => {
      def getLastHeartbeat(): Future[Option[HeartbeatEntity]] = {
        log.debug("[ JobId: {} ] - [Retrieve Last Heartbeat] - Sending find request to Aries REST API.")
        withRetry(ariesRequestRetryCount)(get(s"$ariesBaseUrl/heartbeats/latest?jobId=$jobId", Some(ariesQueryCredentials)).unwrapTo[Seq[HeartbeatEntity]].map(_.headOption)) andThen {
          case Success(_) => log.debug("[ JobId: {} ] - [Retrieve Last Heartbeat] - Heartbeat find succeeded", jobId)
          case Failure(e) => log.error("[ JobId: {} ] - [Retrieve Last Heartbeat] - Failed to find Heartbeat with error: [{}]", jobId, e)
        }
      }

      def buildJobStatusData(job: JobEntity, heartbeat: Option[HeartbeatEntity]): JobStatusData = {
        JobStatusData(
          job.status,
          heartbeat.map(_.currentProgress),
          heartbeat.map(_.estimatedTimeRemaining).flatten,
          job.cortexErrorDetails
        )
      }

      // TODO: refactor this using monad transformers
      val result: Future[Option[JobStatusData]] =
        getJob(jobId) flatMap {
          case Some(job) if job.status == JobStatus.Running =>
            getLastHeartbeat().map(heartbeat => Some(buildJobStatusData(job, heartbeat)))
          case Some(job) => Future.successful(Some(buildJobStatusData(job, None)))
          case None      => Future.successful(None)
        }

      result pipeTo sender

    }

    case FindJob(criteria) => {
      def findBy(criteria: JobSearchCriteria): Future[Seq[JobEntity]] = {
        log.debug("[Find Jobs] - Sending find request to Aries REST API.")
        withRetry(ariesRequestRetryCount)(post(s"$ariesBaseUrl/jobs/search", criteria, Some(ariesQueryCredentials)).unwrapTo[Seq[JobEntity]]) andThen {
          case Success(_) => log.debug("[Find Jobs] - Job finding succeeded")
          case Failure(e) => log.error("[Find Jobs] - Failed to find Jobs using Criteria [{}] with error: [{}]", criteria, e)
        }
      }

      findBy(criteria) pipeTo sender
    }
  }

  def getJob(jobId: UUID): Future[Option[JobEntity]] = {
    log.debug("[ JobId: {} ] - [Retrieve Job] - Sending get request to Aries REST API.", jobId)
    withRetry(ariesRequestRetryCount)(get(s"$ariesBaseUrl/jobs/$jobId", Some(ariesQueryCredentials)).unwrapToOption[JobEntity]) andThen {
      case Success(Some(_)) => log.debug("[ JobId: {} ] - [Retrieve Job] - Job retrieval succeeded.", jobId)
      case Success(None)    => log.warning("[ JobId: {} ] - [Retrieve Job] - Try to retrieve Job but it was not found.", jobId)
      case Failure(e)       => log.error("[ JobId: {} ] - [Retrieve Job] - Failed to retrieve Job with error: {}", jobId, e)
    }
  }

}
