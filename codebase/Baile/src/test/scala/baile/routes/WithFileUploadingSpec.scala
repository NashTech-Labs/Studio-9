package baile.routes

import java.time.Instant

import akka.http.scaladsl.model.{ ContentTypes, HttpEntity }
import akka.http.scaladsl.model.Multipart.FormData
import akka.http.scaladsl.server.directives.FileInfo
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import baile.BaseSpec
import baile.domain.usermanagement.User
import baile.routes.WithFileUploading.MultipartFileHandlingError.{ PartIsMissing, UploadedFileHandlingFailed }
import baile.services.common.FileUploadService
import baile.services.remotestorage.{ File, RemoteStorageService }
import baile.services.usermanagement.util.TestData.SampleUser
import cats.implicits._
import org.mockito.Mockito._
import org.mockito.ArgumentMatchers._

import scala.collection.immutable
import scala.concurrent.{ ExecutionContext, Future }

class WithFileUploadingSpec extends BaseSpec {

  private val remoteStorageService = mock[RemoteStorageService]

  private val sampleRoutes = new WithFileUploading with BaseRoutes {
    override val fileUploadService: FileUploadService =
      new FileUploadService(remoteStorageService, "prefix")
  }

  private val fileContent = randomString(500)
  private val sampleData = FormData.Strict(immutable.Seq(
    FormData.BodyPart.Strict(
      "key1", HttpEntity.Strict(ContentTypes.`text/plain(UTF-8)`, ByteString("value1"))
    ),
    FormData.BodyPart.Strict(
      "file",
      HttpEntity.Strict(ContentTypes.`application/octet-stream`, ByteString(fileContent)),
      Map("filename" → randomString())
    ),
    FormData.BodyPart.Strict(
      "key2", HttpEntity.Strict(ContentTypes.`text/plain(UTF-8)`, ByteString("value2"))
    )
  ))

  when(remoteStorageService.write(
    any[Source[ByteString, Any]],
    any[String]
  )(any[ExecutionContext], any[Materializer])).thenReturn(future(File(randomString(), randomInt(20000), Instant.now)))

  implicit private val user: User = SampleUser

  "WithFileUploading#withMultiPartFile" should {

    "successfully execute handler with right parameters and return its result" in {

      def myHandler(filePath: String, fileInfo: FileInfo, form: Map[String, String]): Future[Either[String, Int]] = {
        form should contain allElementsOf List("key1" -> "value1", "key2" -> "value2")
        future(42.asRight)
      }

      whenReady(
        sampleRoutes.withUploadFile(
          sampleData,
          "file",
          myHandler
        )
      ) { result =>
        result shouldBe 42.asRight
      }

    }

    "return UploadedFileHandlingFailed error when handler returns error" in {

      def myHandler(filePath: String, fileInfo: FileInfo, form: Map[String, String]): Future[Either[String, Int]] =
        future("error".asLeft)

      whenReady(
        sampleRoutes.withUploadFile(
          sampleData,
          "file",
          myHandler
        )
      ) { result =>
        result shouldBe UploadedFileHandlingFailed("error").asLeft
      }

    }

    "return PartIsMissing error when file part was not found" in {
      def myHandler(filePath: String, fileInfo: FileInfo, form: Map[String, String]): Future[Either[String, Int]] =
        future(42.asRight)

      whenReady(
        sampleRoutes.withUploadFile(
          sampleData,
          "foo",
          myHandler
        )
      ) { result =>
        result shouldBe PartIsMissing("foo").asLeft
      }

    }

    "return PartIsMissing error when file part is not a file" in {
      def myHandler(filePath: String, fileInfo: FileInfo, form: Map[String, String]): Future[Either[String, Int]] =
        future(42.asRight)

      whenReady(
        sampleRoutes.withUploadFile(
          sampleData,
          "key2",
          myHandler
        )
      ) { result =>
        result shouldBe PartIsMissing("key2").asLeft
      }

    }

  }

  "WithFileUploading#validateFieldsPresent" should {

    "return no errors when all required fields are there" in {
      val form = Map("key1" -> "value1", "key2" -> "value2")
      sampleRoutes.validateFormFieldsPresent("key2", "key1")(form) shouldBe ().asRight
    }

    "return PartIsMissing error when one of parts is missing" in {
      val form = Map("key1" -> "value1", "key2" -> "value2")
      sampleRoutes.validateFormFieldsPresent("key1", "key2", "key3")(form) shouldBe PartIsMissing("key3").asLeft
    }

  }

}
