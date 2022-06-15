package baile.routes.images

import java.util.UUID

import akka.http.scaladsl.model.{ ContentTypes, HttpEntity, Multipart, StatusCodes }
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import baile.daocommons.WithId
import baile.domain.images.Picture
import baile.domain.images.augmentation.AugmentationType
import baile.domain.usermanagement.User
import baile.routes.RoutesSpec
import baile.services.common.FileUploadService
import baile.services.images.PictureService
import baile.services.images.PictureService.PictureServiceError
import baile.services.images.PictureService.PictureServiceError._
import baile.services.images.util.TestData._
import baile.services.usermanagement.util.TestData.SampleUser
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import play.api.libs.json.{ JsArray, JsObject, JsString, JsValue, Json }

import scala.concurrent.Future

class PictureRoutesSpec extends RoutesSpec {

  implicit val user: User = SampleUser
  val service: PictureService = mock[PictureService]
  val uploadService: FileUploadService = mock[FileUploadService]

  private val albumId = randomString()

  val routes: Route = new PictureRoutes(conf, service, uploadService).routes(albumId)

  when(uploadService.withUploadedFile[PictureServiceError, WithId[Picture]](
    any[Source[ByteString, Nothing]],
    any[String => Future[Either[PictureServiceError, WithId[Picture]]]].apply,
    any[Option[UUID]]
  )(any[Materializer])).thenAnswer { call =>
    call.getArgument[String => Future[Either[PictureServiceError, WithId[Picture]]]](1)(randomString())
  }

  "PUT /albums/:albumId/pictures" should {

    "return success response when keep existing is true" in {
      when(service.addPictures(any[String], any[Seq[Picture]], any[Boolean])(any[User])) thenReturn future(Right(()))
      Put("/pictures", Json.parse(AddPicturesRequestWithKeepExistingTrueAsJson)).signed.check{
        status shouldBe StatusCodes.OK
        responseAs[JsObject] shouldBe JsObject(Seq("id" -> JsString(albumId)))
      }
    }

    "return success response when keep existing is false" in {
      when(service.addPictures(any[String], any[Seq[Picture]], any[Boolean])(any[User])) thenReturn future(Right(()))
      Put("/pictures", Json.parse(AddPicturesRequestWithKeepExistingFalseAsJson)).signed.check{
        status shouldBe StatusCodes.OK
        responseAs[JsObject] shouldBe JsObject(Seq("id" -> JsString(albumId)))
      }
    }

    "return error response when service return no pictures found error" in {
      when(service.addPictures(any[String], any[Seq[Picture]], any[Boolean])(any[User])) thenReturn future(
        Left(PicturesNotFound)
      )
      Put("/pictures", Json.parse(AddPicturesRequestWithKeepExistingFalseAsJson)).signed.check{
        status shouldBe StatusCodes.BadRequest
        validateErrorResponse(responseAs[JsObject])
      }
    }

  }

  "GET /albums/:albumId/pictures/:pictureId" should {

    "return success response" in {
      when(service.get(albumId, "2", Some("1234"))) thenReturn future(Right(PictureEntityWithId))
      when(service.signPicture(any[String], any[WithId[Picture]], any[Option[String]])(any[User])) thenReturn
        future(Right(PictureEntityWithId))
      Get("/pictures/2?shared_resource_id=1234").signed.check {
        status shouldBe StatusCodes.OK
        responseAs[JsObject] shouldBe PictureResponseData
      }
    }

    "return error response" in {
      when(service.get(albumId, "2", Some("1234"))) thenReturn future(Left(PictureNotFound))
      Get("/pictures/2?shared_resource_id=1234").signed.check {
        status shouldBe StatusCodes.NotFound
        validateErrorResponse(responseAs[JsObject])
      }
    }
  }


  "PUT /albums/:albumId/pictures/:pictureId" should {

    "return success response from update picture" in {
      when(service.update(albumId, "2", Some("caption"), Seq())) thenReturn
        future(Right(PictureEntityWithId))
      when(service.signPicture(any[String], any[WithId[Picture]], any[Option[String]])(any[User])) thenReturn
        future(Right(PictureEntityWithId))
      Put("/pictures/2", Json.parse(PictureCreateOrUpdateAsJson)).signed.check {
        status shouldBe StatusCodes.OK
        responseAs[JsObject] shouldBe PictureResponseData
      }
    }

    "return error response from update picture" in {
      when(service.update(albumId, "2", Some("caption"), Seq())) thenReturn
        future(Left(PictureNotFound))
      Put("/pictures/2", Json.parse(PictureCreateOrUpdateAsJson)).signed.check {
        status shouldBe StatusCodes.NotFound
        validateErrorResponse(responseAs[JsObject])
      }
    }
  }


  "DELETE /albums/:albumId/pictures/:pictureId" should {

    "return success response from update picture" in {
      when(service.delete(albumId, "2")) thenReturn future(Right(()))
      Delete("/pictures/2").signed.check {
        status shouldBe StatusCodes.OK
        responseAs[JsObject] shouldBe JsObject(Seq("id" -> JsString("2")))
      }
    }

    "return error response from update picture" in {
      when(service.delete(albumId, "2")) thenReturn
        future(Left(PictureNotFound))
      Delete("/pictures/2").signed.check {
        status shouldBe StatusCodes.NotFound
        validateErrorResponse(responseAs[JsObject])
      }
    }
  }


  "GET /albums/:albumId/pictures/" should {

    "return success response from get pictures" in {
      when(service.list(
        albumId = albumId,
        labels = Some(List("label1", "")),
        search = Some("search"),
        orderBy = Seq(),
        page = 1,
        pageSize = 2,
        sharedResourceId = None,
        augmentationTypes = Some(Seq(Some(AugmentationType.Cropping), None))
      )) thenReturn future(Right((Seq(AugmentedPictureEntityWithId), 1)))
      when(service.signPictures(any[String], any[Seq[WithId[Picture]]], any[Option[String]])(any[User])) thenReturn
        future(Right(Seq(AugmentedPictureEntityWithId)))
      Get("/pictures?search=search&labels=label1,&page=1&page_size=2&augmentations=CROPPING,").signed.check {
        status shouldBe StatusCodes.OK
        val response = responseAs[JsObject]
        response shouldBe AugmentedPicturesListResponse
      }
    }

    "return error response from get pictures" in {
      when(service.list(albumId, None, Some("search"), Seq(), 1, 2, None, None)) thenReturn
        future(Left(PictureNotFound))
      Get("/pictures?search=search&page=1&page_size=2").signed.check {
        status shouldBe StatusCodes.NotFound
        validateErrorResponse(responseAs[JsObject])
      }
    }
  }

  "POST /albums/:id/uploadPicture endpoint" should {
    "return success response" in {
      when(service.create(
        any[String],
        any[String],
        any[String],
        any[String]
      )(any[User], any[Materializer])).thenReturn(future(Right(PictureEntityWithId)))
      when(service.signPicture(any[String], any[WithId[Picture]], any[Option[String]])(any[User])) thenReturn
        future(Right(PictureEntityWithId))
      val pictureName = Multipart.FormData.BodyPart.Strict("filename", "xyz")
      val fileData = Multipart.FormData.BodyPart.Strict(
        "file", HttpEntity(ContentTypes.`text/plain(UTF-8)`, "This is test file"), Map("fileName" -> "abc.txt")
      )
      val formData = Multipart.FormData(fileData, pictureName)
      Post("/uploadPicture", formData).signed.check {
        status shouldBe StatusCodes.OK
        responseAs[JsObject] shouldBe PictureResponseData
      }
    }

    "return error response" in {
      when(service.create(
        any[String],
        any[String],
        any[String],
        any[String]
      )(any[User], any[Materializer])).thenReturn(future(Left(AccessDenied)))
      val pictureName = Multipart.FormData.BodyPart.Strict("filename", "xyz")
      val fileData = Multipart.FormData.BodyPart.Strict(
        "file", HttpEntity(ContentTypes.`text/plain(UTF-8)`, "This is test file"), Map("fileName" -> "abc.txt"))
      val formData = Multipart.FormData(fileData, pictureName)
      Post("/uploadPicture", formData).signed.check {
        status shouldBe StatusCodes.Forbidden
        validateErrorResponse(responseAs[JsObject])
      }
    }
  }

  "GET /albums/:id/tags" should {
    "return data for album" in {
      when(service.getLabelsStats(any[String], any[Option[String]])(any[User]))
        .thenReturn(future(Right(Map("foo" -> 10))))

      Get("/tags").signed.check {
        status shouldBe StatusCodes.OK
        responseAs[JsValue] shouldBe a [JsArray]
        responseAs[JsArray].value.length shouldBe 1
      }
    }
  }

}
