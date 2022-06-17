package baile.services.common

import java.time.Instant
import java.util.UUID

import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import baile.BaseSpec
import baile.services.remotestorage.{ File, S3StorageService }
import org.mockito.Mockito._
import org.mockito.ArgumentMatchers.{ eq => eqTo, _ }
import cats.implicits._

import scala.concurrent.{ ExecutionContext, Future }

class FileUploadServiceSpec extends BaseSpec {

  private val s3StorageService = mock[S3StorageService]
  private val service = new FileUploadService(s3StorageService, "files")

  private val sampleSource = Source(List(ByteString(randomString())))

  when(s3StorageService.write(eqTo(sampleSource), any[String])(any[ExecutionContext], any[Materializer]))
    .thenReturn(future(File(randomString(), randomInt(20000), Instant.now)))

  "FileUploadService#withUploadFile" should {

    "upload file to storage service and just execute handler" in {
      var myState = "initial"
      def myHandler(path: String): Future[Either[Int, Unit]] = {
        myState = "updated"
        Future.successful(().asRight)
      }

      whenReady(service.withUploadedFile(sampleSource, myHandler, Some(UUID.randomUUID()))) { result =>
        result shouldBe ().asRight
        myState shouldBe "updated"
      }
    }

    "upload file to storage service, execute handler and remove the file if an error occurs" in {
      def myHandler(path: String): Future[Either[String, Unit]] = {
        Future.successful("error".asLeft)
      }

      whenReady(service.withUploadedFile(sampleSource, myHandler, Some(UUID.randomUUID()))) { result =>
        result shouldBe "error".asLeft
        verify(s3StorageService).delete(any[String])(any[ExecutionContext])
      }
    }

  }

}
