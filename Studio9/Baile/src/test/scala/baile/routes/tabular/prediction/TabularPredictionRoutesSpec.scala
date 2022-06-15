package baile.routes.tabular.prediction

import java.time.Instant

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import baile.daocommons.WithId
import baile.domain.asset.AssetScope
import baile.domain.tabular.prediction.{ ColumnMapping, TabularPrediction, TabularPredictionStatus }
import baile.domain.usermanagement.User
import baile.routes.RoutesSpec
import baile.routes.contract.tabular.prediction.SimpleMappingPair
import baile.services.common.AuthenticationService
import baile.services.tabular.model.TabularModelService
import baile.services.tabular.prediction.TabularPredictionService
import baile.services.tabular.prediction.TabularPredictionService._
import baile.services.usermanagement.util.TestData.SampleUser
import cats.implicits._
import org.mockito.ArgumentMatchers.{ any, anyInt, anyString }
import org.mockito.Mockito.when
import play.api.libs.json.{ JsObject, JsString, Json }

class TabularPredictionRoutesSpec extends RoutesSpec {

  implicit val user: User = SampleUser

  val prediction = WithId(
    TabularPrediction(
      ownerId = user.id,
      name = "predict-name",
      created = Instant.now(),
      updated = Instant.now(),
      status = TabularPredictionStatus.Running,
      modelId = "model-id",
      inputTableId = "input-table",
      outputTableId = "output-id",
      columnMappings = Seq(
        ColumnMapping(
          trainName = "output",
          currentName = "input"
        )
      ),
      description = Some("description")
    ),
    "id"
  )

  val predictionResponse = Json.obj(
    "id" -> JsString(prediction.id),
    "ownerId" -> JsString(prediction.entity.ownerId.toString),
    "name" -> JsString(prediction.entity.name),
    "status" -> JsString("RUNNING"),
    "created" -> JsString(prediction.entity.created.toString),
    "updated" -> JsString(prediction.entity.updated.toString),
    "modelId" -> JsString(prediction.entity.modelId),
    "input" -> Seq(prediction.entity.inputTableId),
    "output" -> JsString(prediction.entity.outputTableId),
    "columnMappings" -> prediction.entity.columnMappings.map { mapping =>
      SimpleMappingPair(
        sourceColumn = mapping.trainName,
        mappedColumn = mapping.currentName
      )
    },
    "description" -> "description"
  )

  val tabularModelService: TabularModelService = mock[TabularModelService]
  val service: TabularPredictionService = mock[TabularPredictionService]
  val authenticationService: AuthenticationService = mock[AuthenticationService]
  val routes: Route = new TabularPredictionRoutes(conf, authenticationService, service, tabularModelService).routes

  when(authenticationService.authenticate(userToken)).thenReturn(future(Some(SampleUser)))

  "GET /predictions endpoint" should {
    "return success response" in {
      when(service.list(
        any[Option[AssetScope]],
        any[Option[String]],
        any[Seq[String]],
        anyInt(),
        anyInt(),
        any[Option[String]],
        any[Option[String]]
      )(any[User])) thenReturn future((Seq(prediction), 1).asRight)
      when(service.count(None, None, None)) thenReturn future(Right(1))
      Get("/predictions?page=1&page_size=1").signed.check {
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
        future(Left(TabularPredictionServiceError.SortingFieldUnknown))
      }
      Get("/predictions?page=1&page_size=1").signed.check {
        status shouldBe StatusCodes.BadRequest
        validateErrorResponse(responseAs[JsObject])
      }
    }
  }

  "GET /predictions/:id endpoint" should {
    "return success response" in {
      when(service.get(
        anyString(),
        any[Option[String]]
      )(any[User])) thenReturn future(Right(prediction))
      Get("/predictions/id").signed.check {
        status shouldBe StatusCodes.OK
      }
    }

    "return error response when error comes" in {
      when(service.get(
        anyString(),
        any[Option[String]]
      )(any[User])) thenReturn future(Left(TabularPredictionServiceError.AccessDenied))
      Get("/predictions/id").signed.check {
        status shouldBe StatusCodes.Forbidden
        validateErrorResponse(responseAs[JsObject])
      }
    }
  }

  "DELETE /predictions/:id endpoint" should {
    "return success response" in {
      when(service.delete(anyString())(any[User])) thenReturn future(Right(()))
      Delete("/predictions/id").signed.check {
        status shouldBe StatusCodes.OK
        responseAs[JsObject] shouldBe JsObject(Seq("id" -> JsString("id")))
      }
    }

    "return error response when error comes" in {
      when(service.delete(anyString())(any[User])) thenReturn future(Left(TabularPredictionServiceError.AccessDenied))
      Delete("/predictions/id").signed.check {
        status shouldBe StatusCodes.Forbidden
        validateErrorResponse(responseAs[JsObject])
      }
    }
  }

  "PUT /predictions/:id endpoint" should {
    "return success response" in {
      when(service.update(anyString(), any[Option[String]], any[Option[String]])(any[User])) thenReturn
        future(Right(prediction))
      Put("/predictions/id",  Json.parse("{\"name\": \"foo\"}")).signed.check {
        status shouldBe StatusCodes.OK
        responseAs[JsObject] shouldBe predictionResponse
      }
    }

    "return error response when error comes" in {
      when(service.update(anyString(), any[Option[String]], any[Option[String]])(any[User])) thenReturn
        future(Left(TabularPredictionServiceError.PredictionNameCanNotBeEmpty))
      Put("/predictions/id",  Json.parse("{\"name\": \"foo\"}")).signed.check {
        status shouldBe StatusCodes.BadRequest
        validateErrorResponse(responseAs[JsObject])
      }
    }
  }

  "POST /predictions/:id/save endpoint" should {
    "return success response" in {
      when(service.save(anyString())(any[User])) thenReturn future(Right(()))
      Post("/predictions/id/save").signed.check {
        status shouldBe StatusCodes.OK
        responseAs[JsObject] shouldBe JsObject(Seq("id" -> JsString("id")))
      }
    }

    "return error response when error comes" in {
      when(service.save(anyString())(any[User])) thenReturn
        future(Left(TabularPredictionServiceError.TableNotFound))
      Post("/predictions/id/save").signed.check {
        status shouldBe StatusCodes.BadRequest
        validateErrorResponse(responseAs[JsObject])
      }
    }
  }

  "POST /predictions endpoint" should {

    "return success response" in {
      when(service.create(
        any[Option[String]],
        anyString(),
        anyString(),
        any[Seq[ColumnMapping]],
        any[Option[String]]
      )(any[User])) thenReturn {
        future(prediction.asRight)
      }
      val requestJson =
        """
          |{
          |"modelId": "model-id",
          |"input": "input",
          |"mappingType": "INPUTMATRIX",
          |"name": "name",
          |"columnMappings": [{
          |"mappedColumn": "prediction_source",
          |"sourceColumn": "train"
          |}]
          |}
        """.stripMargin
      Post("/predictions", Json.parse(requestJson)).signed.check {
        status shouldBe StatusCodes.OK
        responseAs[JsObject] shouldBe predictionResponse
      }
    }

    "return error response" in {
      when(service.create(
        any[Option[String]],
        anyString(),
        anyString(),
        any[Seq[ColumnMapping]],
        any[Option[String]]
      )(any[User])) thenReturn {
        future(
          TabularPredictionCreateError.TableNotFound.asLeft
        )
      }
      val requestJson =
        """
          |{
          |"modelId": "model-id",
          |"name": "name",
          |"input": "input",
          |"mappingType":"INPUTMATRIX",
          |"columnMappings":[]
          |}
        """.stripMargin
      Post("/predictions", Json.parse(requestJson)).signed.check {
        status shouldBe StatusCodes.BadRequest
        validateErrorResponse(responseAs[JsObject])
      }
    }

  }

}
