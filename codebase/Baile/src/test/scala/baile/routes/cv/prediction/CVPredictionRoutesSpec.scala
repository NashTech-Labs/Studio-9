package baile.routes.cv.prediction

import java.time.Instant

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import baile.daocommons.WithId
import baile.domain.asset.AssetScope
import baile.domain.cv.prediction.{ CVPrediction, CVPredictionStatus }
import baile.domain.usermanagement.User
import baile.routes.RoutesSpec
import baile.routes.cv.util.TestData._
import baile.services.common.AuthenticationService
import baile.services.usermanagement.util.TestData.SampleUser
import baile.services.cv.prediction.CVPredictionService
import baile.services.cv.prediction.CVPredictionService.{ CVPredictionCreateError, CVPredictionServiceError }
import baile.domain.cv.prediction.CVModelPredictOptions
import cats.implicits._
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito.when
import play.api.libs.json.{ JsObject, JsString, Json }

class CVPredictionRoutesSpec extends RoutesSpec {
  implicit val user: User = SampleUser
  val service: CVPredictionService = mock[CVPredictionService]
  val authenticationService = mock[AuthenticationService]
  val routes: Route = new CVPredictionRoutes(conf, authenticationService, service).routes

  when(authenticationService.authenticate(userToken)).thenReturn(future(Some(SampleUser)))
  val entity = WithId(
    CVPrediction(
      SampleUser.id,
      "name",
      CVPredictionStatus.Done,
      Instant.now(),
      Instant.now(),
      description = None,
      "model-id",
      "input",
      "output",
      None,
      None,
      None,
      None,
      None
    ), "42669c4f-668a-4dca-b312-f46acd71d53f"
  )

  "GET /cv-predictions endpoint" should {
    "return success response" in {
      when(service.list(
        any[Option[AssetScope]],
        any[Option[String]],
        any[Seq[String]],
        anyInt(),
        anyInt(),
        any[Option[String]],
        any[Option[String]]
      )(any[User])) thenReturn future((Seq(CVPredictionWithIdEntity), 1).asRight)
      when(service.count(None, None, None)) thenReturn future(Right(1))
      Get("/cv-predictions?page=1&page_size=1").signed.check{
        status shouldBe StatusCodes.OK
        val response = responseAs[JsObject]
        response.keys should contain allOf("data", "count")
        (response \ "count").as[Int] shouldBe 1
      }
    }

    "return error response when error comes in get list" in {
      when(service.list(
        any[Option[AssetScope]],
        any[Option[String]],
        any[Seq[String]],
        anyInt(),
        anyInt(),
        any[Option[String]],
        any[Option[String]]
      )(any[User])) thenReturn {
        future(Left(CVPredictionServiceError.SortingFieldUnknown))
      }
      Get("/cv-predictions?page=1&page_size=1").signed.check{
        status shouldBe StatusCodes.BadRequest
        responseAs[JsObject].keys should contain allOf("code", "message")
      }
    }
  }

  "GET /cv-predictions/:id endpoint" should {
    "return success response" in {
      when(service.get(anyString(),any[Option[String]])(any[User])) thenReturn future(Right(CVPredictionWithIdEntity))
      Get("/cv-predictions/id?shared_resource_id=1234").signed.check{
        status shouldBe StatusCodes.OK
        responseAs[JsObject] shouldBe CVPredictionResponseData
      }
    }

    "return error response when error comes" in {
      when(service.list(
        any[Option[AssetScope]],
        any[Option[String]],
        any[Seq[String]],
        anyInt(), anyInt(),
        any[Option[String]],
        any[Option[String]]
      )(any[User])) thenReturn {
        future(Left(CVPredictionServiceError.AccessDenied))
      }
      Get("/cv-predictions?page=1&page_size=1").signed.check{
        status shouldBe StatusCodes.Forbidden
        responseAs[JsObject].keys should contain allOf("code", "message")
      }
    }
  }

  "DELETE /cv-predictions/:id endpoint" should {
    "return success response" in {
      when(service.delete(anyString())(any[User])) thenReturn future(Right(()))
      Delete("/cv-predictions/id").signed.check{
        status shouldBe StatusCodes.OK
        responseAs[JsObject] shouldBe JsObject(Seq("id" -> JsString("id")))
      }
    }

    "return error response when error comes" in {
      when(service.delete(anyString())(any[User])) thenReturn future(Left(CVPredictionServiceError.AccessDenied))
      Delete("/cv-predictions/id").signed.check{
        status shouldBe StatusCodes.Forbidden
        responseAs[JsObject].keys should contain allOf("code", "message")
      }
    }
  }

  "POST /cv-predictions endpoint" should {

    "return success response" in {
      val now = Instant.now()
      when(service.create(
        anyString(),
        anyString(),
        any[Option[String]],
        any[Option[String]],
        any[Option[String]],
        any[Option[CVModelPredictOptions]],
        anyBoolean()
      )(any[User])) thenReturn {
        future(
          WithId(
            CVPrediction(
              SampleUser.id,
              "name",
              CVPredictionStatus.Done,
              now,
              now,
              description = None,
              "model-id",
              "input",
              "output",
              None,
              None,
              None,
              None,
              None
            ), "42669c4f-668a-4dca-b312-f46acd71d53f"
          ).asRight
        )
      }

      val requestJson =
        """
          |{
          |"modelId": "model-id",
          |"name": "name",
          |"input": "input",
          |"options": {
          |   "loi" : [ { "label": "label1", "threshold": 0.4 } ]
          | },
          |"evaluate": true
          |}
        """.stripMargin
      Post("/cv-predictions", Json.parse(requestJson)).signed.check{
        status shouldBe StatusCodes.OK
        responseAs[JsObject].keys should contain allOf(
          "id", "ownerId", "name", "status", "created", "updated", "modelId", "input", "output"
        )
      }
    }

    "return error response" in {
      when(service.create(
        anyString(),
        anyString(),
        any[Option[String]],
        any[Option[String]],
        any[Option[String]],
        any[Option[CVModelPredictOptions]],
        anyBoolean()
      )(any[User])) thenReturn {
        future(
          CVPredictionCreateError.AlbumNotFound.asLeft
        )
      }
      val requestJson =
        """
          |{
          |"modelId": "model-id",
          |"name": "name",
          |"input": "input"
          |}
        """.stripMargin
      Post("/cv-predictions", Json.parse(requestJson)).signed.check{
        status shouldBe StatusCodes.BadRequest
        responseAs[JsObject].keys should contain allOf("code", "message", "errors")
      }
    }

  }

  "PUT /cv-predictions/:id endpoint" should {

    "return success response" in {
      when(service.update(
        "id",
        Some("newName"),
        None
      )) thenReturn future(entity.asRight)

      Put("/cv-predictions/id", Json.parse("""{"name":"newName"}""")).signed.check{
        status shouldBe StatusCodes.OK
        responseAs[JsObject].keys should contain allOf(
          "id", "ownerId", "name", "status", "created", "updated", "modelId", "input", "output"
        )
      }
    }

    "return error response when new name is empty" in {
      when(service.update(
        "id",
        Some(""),
        None
      )) thenReturn future(CVPredictionServiceError.PredictionNameCanNotBeEmpty.asLeft)

      Put("/cv-predictions/id", Json.parse("""{"name":""}""")).signed.check {
        status shouldBe StatusCodes.BadRequest
        responseAs[JsObject].keys should contain allOf("code", "message", "errors")
      }
    }

  }

}
