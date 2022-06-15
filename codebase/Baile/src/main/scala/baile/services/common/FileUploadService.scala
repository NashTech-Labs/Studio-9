package baile.services.common

import java.text.SimpleDateFormat
import java.util.{ Date, UUID }

import akka.event.LoggingAdapter
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import baile.services.remotestorage.RemoteStorageService
import baile.utils.ThrowableExtensions._

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }

class FileUploadService(
  val fileStorage: RemoteStorageService,
  filePathPrefix: String
)(implicit ec: ExecutionContext, logger: LoggingAdapter) {

  def withUploadedFile[E, R](
    fileSource: Source[ByteString, Any],
    handler: String => Future[Either[E, R]],
    userId: Option[UUID]
  )(implicit materializer: Materializer): Future[Either[E, R]] = {

    val filePath = {
      val date = new Date
      val dateStamp = new SimpleDateFormat("yyyyMMdd").format(date)
      val userPrefix = userId.fold("")(_.toString)
      val randomSuffix = UUID.randomUUID().toString
      fileStorage.path(filePathPrefix, userPrefix, dateStamp, randomSuffix)
    }

    def cleanUp(): Future[Unit] = fileStorage.delete(filePath)

    def handleAndCleanUp(): Future[Either[E, R]] = {
      val result = handler(filePath)
      result.onComplete {
        case Success(Left(_)) => cleanUp()
        case Failure(_) => cleanUp()
        case Success(Right(_)) => ()
      }
      result
    }

    for {
      _ <- fileStorage.write(fileSource, filePath)
      result <- handleAndCleanUp()
    } yield result
  }

  private[services] def deleteUploadedFile(filePath: String): Future[Unit] =
    fileStorage.delete(filePath).recover {
      case ex =>
        logger.warning(
          "Failed to delete uploaded file [{}]. Probably file was already deleted at this point. Error: [{}]",
          filePath,
          ex.printInfo
        )
    }


}
