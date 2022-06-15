package baile.routes.images

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import baile.domain.images.AlbumLabelMode.Classification
import baile.domain.usermanagement.User
import baile.routes.RoutesSpec
import baile.services.usermanagement.util.TestData.SampleUser
import baile.services.images.AlbumService
import baile.services.images.AlbumService.AlbumNameValidationError._
import baile.services.images.AlbumService.AlbumServiceError
import baile.services.images.AlbumService.AlbumServiceError._
import baile.services.images.util.TestData._
import org.mockito.Mockito.when
import play.api.libs.json.{ JsObject, JsString, Json }
import cats.implicits._

class AlbumRoutesSpec extends RoutesSpec {

  implicit val user: User = SampleUser
  val service: AlbumService = mock[AlbumService]
  val routes: Route = new AlbumRoutes(conf, service).routes()

  when(service.signAlbum(AlbumEntityWithId)) thenReturn AlbumEntityWithId

  "GET /albums/:id" should {

    "return success response" in {
      when(service.get("1", Some("1234"))) thenReturn future(Right(AlbumEntityWithId))
      Get("/albums/1?shared_resource_id=1234").signed.check {
        status shouldBe StatusCodes.OK
        responseAs[JsObject] shouldBe AlbumResponseData
      }
    }

    "return error response" in {
      when(service.get("1", Some("1234"))) thenReturn future(Left(AlbumServiceError.AlbumNotFound))
      Get("/albums/1?shared_resource_id=1234").signed.check {
        status shouldBe StatusCodes.NotFound
        validateErrorResponse(responseAs[JsObject])
      }
    }
  }

  "GET /albums" should {

    "return success response" in {
      when(service.list(None, Some("search"), Seq(), 1, 2, None, None)) thenReturn
        future(Right((Seq(AlbumEntityWithId), 1)))
      Get("/albums?search=search&page=1&page_size=2").signed.check {
        status shouldBe StatusCodes.OK
        val response = responseAs[JsObject]
        response.keys should contain allOf("data", "count")
        (response \ "count").as[Int] shouldBe 1
      }
    }

    "return error response" in {
      when(service.list(None, Some("search"), Seq(), 1, 2, None, None)) thenReturn
        future(Left(AlbumServiceError.AccessDenied))
      Get("/albums?search=search&page=1&page_size=2").signed.check {
        status shouldBe StatusCodes.Forbidden
        validateErrorResponse(responseAs[JsObject])
      }
    }
  }

  "DELETE /albums/:id" should {

    "return success response from delete album" in {
      when(service.delete("1")) thenReturn future(Right(()))
      Delete("/albums/1").signed.check {
        status shouldBe StatusCodes.OK
        responseAs[JsObject] shouldBe JsObject(Seq("id" -> JsString("1")))
      }
    }

    "return error response from delete album" in {
      when(service.delete("1")) thenReturn future(Left(AlbumServiceError.AlbumNotFound))
      Delete("/albums/1").signed.check {
        status shouldBe StatusCodes.NotFound
        validateErrorResponse(responseAs[JsObject])
      }
    }
  }

  "PUT /albums/:id" should {

    "return success response for successful update" in {
      when(service.update("1", Some("name"), None, Some(Classification))) thenReturn
        future(Right(AlbumEntityWithId))
      Put("/albums/1", Json.parse(AlbumUpdateRequestAsJson)).signed.check {
        status shouldBe StatusCodes.OK
        responseAs[JsObject] shouldBe AlbumResponseData
      }
    }

    "return error response for not found album" in {
      when(service.update("1", Some("name"), None, Some(Classification))) thenReturn
        future(Left(AlbumServiceError.AlbumNotFound))
      Put("/albums/1", Json.parse(AlbumUpdateRequestAsJson)).signed.check {
        status shouldBe StatusCodes.NotFound
        validateErrorResponse(responseAs[JsObject])
      }
    }

    "return error response for forbidden album" in {
      when(service.update("1", Some("name"), None, Some(Classification))) thenReturn
        future(Left(AlbumServiceError.AccessDenied))
      Put("/albums/1", Json.parse(AlbumUpdateRequestAsJson)).signed.check {
        status shouldBe StatusCodes.Forbidden
        validateErrorResponse(responseAs[JsObject])
      }
    }

    "return error response for name taken case" in {
      when(service.update("1", Some("name"), None, Some(Classification))) thenReturn
        future(Left(AlbumNameTaken))
      Put("/albums/1", Json.parse(AlbumUpdateRequestAsJson)).signed.check {
        status shouldBe StatusCodes.BadRequest
        validateErrorResponse(responseAs[JsObject])
      }
    }

    "return error response for label mode locked error" in {
      when(service.update("1", Some("name"), None, Some(Classification))) thenReturn
        future(Left(AlbumServiceError.AlbumLabelModeLocked("foo")))
      Put("/albums/1", Json.parse(AlbumUpdateRequestAsJson)).signed.check {
        status shouldBe StatusCodes.BadRequest
        validateErrorResponse(responseAs[JsObject])
      }
    }
  }

  "POST /albums" should {

    "return success response from create album" in {
      when(service.create(Some("name"), Classification, None, None, None)) thenReturn
        future(Right(AlbumEntityWithId))
      Post("/albums", Json.parse(AlbumCreateRequestAsJson)).signed.check {
        status shouldBe StatusCodes.OK
        responseAs[JsObject] shouldBe AlbumResponseData
      }
    }
  }

  "POST /albums/:id/copy" should {

    "return 200 response with album" in {
      val request =
        """
          |{
          |"name": "newName",
          |"description": "new descritption",
          |"copyOnlyLabelledPictures": true
          |}
        """.stripMargin
      when(service.clone("id", None, Some("newName"), Some("new descritption"), true, None, Some("1"))) thenReturn
        future(AlbumEntityWithId.asRight)
      Post("/albums/id/copy?shared_resource_id=1", Json.parse(request)).signed.check {
        status shouldEqual StatusCodes.OK
        responseAs[JsObject] shouldBe AlbumResponseData
      }
    }

    "return 200 response with album for pictures selected" in {
      val request =
        """
          |{
          |"pictureIds": ["1", "2"],
          |"name": "newName"
          |}
        """.stripMargin
      when(service.clone("id", Some(Seq("1", "2")), Some("newName"), None, false, None, Some("1"))) thenReturn
        future(AlbumEntityWithId.asRight)
      Post("/albums/id/copy?shared_resource_id=1", Json.parse(request)).signed.check {
        status shouldEqual StatusCodes.OK
        responseAs[JsObject] shouldBe AlbumResponseData
      }
    }

    "return error response when album name is empty" in {
      val request =
        """
          |{
          |"pictureIds": ["1", "2"],
          |"name": ""
          |}
        """.stripMargin
      when(service.clone("id", Some(Seq("1", "2")), Some(""), None, false, None, Some("1"))) thenReturn
        future(AlbumNameIsEmpty.asLeft)
      Post("/albums/id/copy?shared_resource_id=1", Json.parse(request)).signed.check {
        status shouldBe StatusCodes.BadRequest
        validateErrorResponse(responseAs[JsObject])
      }
    }

  }

  "POST /albums/:id/augment" should {

    "return success response to augment picture" in {
      val request =
        """
          |{
          |"outputName": "augmentedAlbum",
          |"includeOriginalPictures": true,
          |"bloatFactor": 1,
          |"augmentations": [{
          |"augmentationType" : "ROTATION",
          |"angles" : [30, 60],
          |"resize" : true,
          |"bloatFactor" : 1
          |}]
          |}
        """.stripMargin
      when(service.augmentPictures(
        AugmentationsParamTestData,
        Some("augmentedAlbum"),
        "id",
        includeOriginalPictures = true,
        bloatFactor = 1,
        inLibrary = None
      )) thenReturn
        future(OutputAlbumEntityWithId.asRight)
      when(service.signAlbum(OutputAlbumEntityWithId)) thenReturn OutputAlbumEntityWithId
      Post("/albums/id/augment", Json.parse(request)).signed.check {
        status shouldBe StatusCodes.OK
        responseAs[JsObject] shouldBe AugmentedAlbumResponse
      }
    }

    "return error response when invalid augmentation request params are provided" in {
      val request =
        """
          |{
          |"outputName": "augmentedAlbum",
          |"includeOriginalPictures": true,
          |"bloatFactor": 1,
          |"augmentations": [{
          |"augmentationType" : "ROTATION",
          |"angles" : [30, 1960],
          |"resize" : true,
          |"bloatFactor" : 1
          |}]
          |}
        """.stripMargin
      when(service.augmentPictures(
        InvalidAugmentationsParamTestData,
        Some("augmentedAlbum"),
        "id",
        includeOriginalPictures = true,
        bloatFactor = 1,
        inLibrary = None
      )) thenReturn
        future(InvalidAugmentationRequestParamError("Invalid Request Params").asLeft)
      Post("/albums/id/augment", Json.parse(request)).signed.check {
        status shouldBe StatusCodes.BadRequest
        validateErrorResponse(responseAs[JsObject])
      }
    }


  }

  "POST /albums/:id/save" should {

    "return success response to save album" in {
      val request =
        """
          |{
          |"name": "name"
          |}
        """.stripMargin

      when(service.save(
        "id",
        "name"
      )) thenReturn
        future(AlbumEntityWithId.asRight)
      Post("/albums/id/save", Json.parse(request)).signed.check {
        status shouldBe StatusCodes.OK
        responseAs[JsObject] shouldBe AlbumResponseData
      }

    }

    "return error response to save album" in {
      val request =
        """
          |{
          |"name": ""
          |}
        """.stripMargin

      when(service.save(
        "id",
        ""
      )) thenReturn
        future(AlbumNameIsEmpty.asLeft)
      Post("/albums/id/save", Json.parse(request)).signed.check {
        status shouldBe StatusCodes.BadRequest
        validateErrorResponse(responseAs[JsObject])
      }

    }

    "return error response to save album when name is already taken" in {
      val request =
        """
          |{
          |"name": "name"
          |}
        """.stripMargin

      when(service.save(
        "id",
        "name"
      )) thenReturn
        future(AlbumNameTaken.asLeft)
      Post("/albums/id/save", Json.parse(request)).signed.check {
        status shouldBe StatusCodes.BadRequest
        validateErrorResponse(responseAs[JsObject])
      }
    }
  }

  "GET /albums/:id/access-param" should {

    "return success response" in {
      when(service.generateAlbumStorageAccessParams("1234")) thenReturn
        future(Right(AlbumStorageAccessParametersEntity))

      Get("/albums/1234/access-param").signed.check {
        status shouldBe StatusCodes.OK
        responseAs[JsObject] shouldBe AlbumStorageAccessParametersResponseData
      }
    }

  }

}
