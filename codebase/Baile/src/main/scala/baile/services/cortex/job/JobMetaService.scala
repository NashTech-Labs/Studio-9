package baile.services.cortex.job

import java.text.SimpleDateFormat
import java.util.{ Date, UUID }

import akka.event.LoggingAdapter
import baile.services.remotestorage.RemoteStorageService
import baile.utils.TryExtensions._
import com.typesafe.config.Config
import cortex.api.job.JobRequest

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }

class JobMetaService(
  conf: Config,
  remoteStorage: RemoteStorageService
)(implicit logger: LoggingAdapter, ec: ExecutionContext) {

  private val baseInputPath = conf.getString("cortex.job.dir")

  def writeMeta(jobId: UUID, request: JobRequest): Future[String] = {
    val fullPath = buildFullPath(jobId)

    val result = for {
      serializedRequest <- Try(request.toByteArray).toFuture
      _ <- remoteStorage.write(serializedRequest, fullPath)
    } yield fullPath

    val step = "Create Job"
    result.onComplete {
      case Success(fullPath) =>
        JobLogging.info(
          jobId,
          s"Successfully wrote job request metadata to $fullPath",
          step
        )
      case Failure(throwable) =>
        JobLogging.error(
          jobId,
          s"Failed to wrote job request metadata to $fullPath with error: $throwable",
          step
        )
    }

    result
  }

  def readRawMeta(jobId: UUID, outputPath: String): Future[Array[Byte]] = {
    val result = remoteStorage.read(outputPath)

    val step = "Read Job Result"
    result.onComplete {
      case Success(_) =>
        JobLogging.info(
          jobId,
          s"Successfully read job result metadata from $outputPath",
          step
        )
      case Failure(throwable) =>
        JobLogging.error(
          jobId,
          s"Failed to read job request metadata from $outputPath with error: $throwable",
          step
        )
    }

    result
  }

  private def buildFullPath(jobId: UUID): String = {
    val date = new Date
    val dateStamp = new SimpleDateFormat("yyyyMMdd").format(date)
    s"$baseInputPath/$dateStamp/$jobId/params.dat"
  }

}
