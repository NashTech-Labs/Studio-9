package cortex.jobmaster.orion.service

import java.nio.file.{ Files, Paths }
import java.util.UUID

import com.trueaccord.scalapb.GeneratedMessage
import cortex.api.job.JobRequest
import cortex.common.logging.JMLoggerFactory
import cortex.common.Logging
import cortex.common.future.FutureExtensions
import cortex.jobmaster.orion.service.domain.JobRequestHandler
import cortex.jobmaster.orion.service.io.PbByteArrayUnmarshaller
import cortex.scheduler.TaskScheduler
import cortex.jobmaster.jobs.time.JobTimeInfo
import org.apache.commons.lang3.exception.ExceptionUtils

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }

class CliJobService(
    jobId:             String,
    filePath:          String,
    jobRequestHandler: JobRequestHandler,
    taskScheduler:     TaskScheduler
)(implicit val ec: ExecutionContext, val loggerFactory: JMLoggerFactory)
  extends PbByteArrayUnmarshaller[JobRequest]
  with FutureExtensions
  with Logging {

  def start(): Future[(_ <: GeneratedMessage, JobTimeInfo)] = {
    val maybeJobRequest = Try {
      val serializedJobMessage = Files.readAllBytes(Paths.get(filePath))
      log.info(s"retrieving job request from local path: $filePath")
      unmarshall(serializedJobMessage)
    }

    for {
      jobRequest <- maybeJobRequest.toFuture
      result <- startJob(UUID.fromString(jobId), jobRequest)
    } yield result
  }

  def stop(): Unit = {
    taskScheduler.stop()
  }

  protected def startJob(jobId: UUID, jobRequest: JobRequest): Future[(_ <: GeneratedMessage, JobTimeInfo)] = {
    jobRequestHandler.handleJobRequest((jobId.toString, jobRequest)) andThen {
      case Success((result, _)) =>
        log.info(s"job finished successfully")
        log.info(s"[Job Id: $jobId] job result:\n$result\n")

      case Failure(e) =>
        log.error(s"job failed ${e.getMessage}")
        log.error(s"job failed ${ExceptionUtils.getStackTrace(e)}")
    } andThen {
      case _ =>
        stop()
    }
  }
}
