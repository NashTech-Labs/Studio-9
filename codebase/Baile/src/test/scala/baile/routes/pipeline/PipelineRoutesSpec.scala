package baile.routes.pipeline

import java.time.Instant

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import baile.daocommons.WithId
import baile.domain.pipeline.PipelineParams._
import baile.domain.pipeline.{
  Pipeline,
  PipelineOutputReference,
  PipelineStatus,
  PipelineStep,
  PipelineStepInfo
}
import baile.routes.ExtendedRoutesSpec
import baile.services.usermanagement.util.TestData.SampleUser
import baile.services.pipeline.PipelineService
import baile.services.pipeline.PipelineService.PipelineServiceError
import baile.services.pipeline.PipelineService.PipelineServiceError.{ AccessDenied, PipelineNotFound }
import org.scalatest.Assertion
import play.api.libs.json._

class PipelineRoutesSpec extends ExtendedRoutesSpec {

  trait Setup extends RoutesSetup { self =>

    val pipelineService: PipelineService = mock[PipelineService]
    val routes: Route = new PipelineRoutes(
      conf,
      authenticationService,
      pipelineService
    ).routes

    def createPipelineWithId(): WithId[Pipeline] = {
      val dateTime = Instant.now()
      val pipelineId = "pipelineId"
      val pipeline = Pipeline(
        name = "new_pipeline",
        ownerId = SampleUser.id,
        status = PipelineStatus.Idle,
        created = dateTime,
        updated = dateTime,
        inLibrary = true,
        description = Some("description of pipeline"),
        steps = Seq(PipelineStepInfo(
          step = PipelineStep(
            id = "step1",
            operatorId = "operatorId",
            inputs = Map(
              "operatorName" -> PipelineOutputReference(
                "stepId",
                1
              )
            ),
            params = Map(
              "paramName1" -> StringParam("paramter1"),
              "paramName2" -> StringParams(Seq("paramter1")),
              "paramName3" -> IntParam(1),
              "paramName4" -> IntParams(Seq(1, 2)),
              "paramName5" -> FloatParam(1.0F),
              "paramName6" -> FloatParams(Seq(1.0F, 2.0F)),
              "paramName7" -> BooleanParam(true),
              "paramName8" -> BooleanParams(Seq(true, false)),
              "paramName9" -> EmptySeqParam
            ),
            coordinates = None
          ),
          pipelineParameters = Map(
            "paramName10" -> "Pipeline Parameter 10",
            "paramName11" -> "Pipeline Parameter 11"
          ))
        )
      )

      WithId(pipeline, pipelineId)
    }

    def validateCreatePipelineResponse(pipeline: WithId[Pipeline], response: JsObject): Assertion = {
      response.fields should contain allOf(
        "id" -> JsString(pipeline.id),
        "name" -> JsString(pipeline.entity.name),
        "ownerId" -> JsString(pipeline.entity.ownerId.toString)
      )
      Instant.parse((response \ "created").as[String]) shouldBe pipeline.entity.created
      Instant.parse((response \ "updated").as[String]) shouldBe pipeline.entity.updated
    }

  }

  "GET /pipelines/:id" should {

    "return success response" in new Setup {
      val pipelineWithId = createPipelineWithId()
      pipelineService.get("pipelineId", *) shouldReturn future(Right(pipelineWithId))
      Get("/pipelines/pipelineId").signed.check {
        status shouldBe StatusCodes.OK
        validateCreatePipelineResponse(pipelineWithId, responseAs[JsObject])
      }
    }

    "return error response when pipeline not found" in new Setup {
      pipelineService.get("pipelineId", *) shouldReturn future(Left(PipelineNotFound))
      Get("/pipelines/pipelineId").signed.check {
        status shouldBe StatusCodes.NotFound
        validateErrorResponse(responseAs[JsObject])
      }
    }

    "return error response when user is not owner of pipeline" in new Setup {
      pipelineService.get("pipelineId", *) shouldReturn future(Left(AccessDenied))
      Get("/pipelines/pipelineId").signed.check {
        status shouldBe StatusCodes.Forbidden
        validateErrorResponse(responseAs[JsObject])
      }
    }
  }

  "GET /pipelines" should {

    "return success response" in new Setup {
      val pipelineWithId = createPipelineWithId()

      pipelineService.list(*, *, *, *, *, *, *) shouldReturn future(Right((Seq(pipelineWithId), 1)))
      Get("/pipelines").signed.check {
        status shouldBe StatusCodes.OK
        val response = responseAs[JsObject]
        response.keys should contain allOf("data", "count")
        (response \ "count").as[Int] shouldBe 1
      }
    }
  }

  "PUT /pipelines/:id" should {

    "return success response from update project" in new Setup {
      val pipelineWithId = createPipelineWithId()
      val pipelineUpdateJson: JsValue = Json.parse(
        """{
          | "name": "newName",
          |	"steps": [{
          |		"id": "step1",
          |		"operator": "operatorId",
          |		"inputs": {
          |			"operatorName": {
          |				"stepId": "stepId",
          |				"outputIndex": 1
          |			}
          |		},
          |		"params": {
          |			"paramName3": 1,
          |			"paramName9": [],
          |			"paramName7": true,
          |			"paramName2": ["paramter1"],
          |			"paramName5": 1,
          |			"paramName6": [1, 2],
          |			"paramName1": "paramter1",
          |			"paramName4": [1, 2],
          |			"paramName8": [true, false]
          |		},
          |   "pipelineParameters": {
          |     "paramName10": "Pipeline Parameter 10",
          |     "paramName11": "Pipeline Parameter 11"
          |   }
          |	}]
          | }
        """.stripMargin
      )
      pipelineService.update("pipelineId", Some("newName"), *, *) shouldReturn future(Right(pipelineWithId))
      Put("/pipelines/pipelineId", pipelineUpdateJson).signed.check {
        status shouldBe StatusCodes.OK
        validateCreatePipelineResponse(pipelineWithId, responseAs[JsObject])
      }
    }

    "return error response from update pipeline when pipeline not found" in new Setup {
      val pipelineCreatedAndUpdatedJson: JsValue = Json.parse("""{"name": "newName", "steps": []}""")

      pipelineService.update("pipelineId", Some("newName"), *, *) shouldReturn future(Left(PipelineNotFound))
      Put("/pipelines/pipelineId", pipelineCreatedAndUpdatedJson).signed.check {
        status shouldBe StatusCodes.NotFound
        validateErrorResponse(responseAs[JsObject])
      }
    }
  }

  "POST /pipelines" should {

    "create pipeline successfully" in new Setup {
      val pipelineWithId = createPipelineWithId()
      val pipelineCreatedJson: JsValue = Json.parse(
        """{
          | "name": "new_pipeline",
          |	"description": "description of pipeline",
          |	"steps": [{
          |		"id": "step1",
          |		"operator": "operatorId",
          |		"inputs": {
          |			"operatorName": {
          |				"stepId": "stepId",
          |				"outputIndex": 1
          |			}
          |		},
          |		"params": {
          |			"paramName3": 1,
          |			"paramName9": [],
          |			"paramName7": true,
          |			"paramName2": ["paramter1"],
          |			"paramName5": 1,
          |			"paramName6": [1, 2],
          |			"paramName1": "paramter1",
          |			"paramName4": [1, 2],
          |			"paramName8": [true, false]
          |		},
          |   "pipelineParameters": {
          |     "paramName10": "Pipeline Parameter 10",
          |     "paramName11": "Pipeline Parameter 11"
          |   }
          |	}]
          | }
        """.stripMargin
      )

      pipelineService.create(Some("new_pipeline"), *, *, *) shouldReturn future(Right(pipelineWithId))
      Post("/pipelines", pipelineCreatedJson).signed.check {
        status shouldBe StatusCodes.OK
        validateCreatePipelineResponse(pipelineWithId, responseAs[JsObject])
      }
    }

    "not create the pipeline when project with same name already exists" in new Setup {
      val pipelineCreatedAndUpdatedJson: JsValue = Json.parse("""{"name": "newName", "steps": []}""")

      pipelineService.create(Some("newName"), *, *, *) shouldReturn
        future(Left(PipelineServiceError.NameIsTaken))
      Post("/pipelines", pipelineCreatedAndUpdatedJson).signed.check {
        status shouldBe StatusCodes.BadRequest
        validateErrorResponse(responseAs[JsObject])
      }
    }
  }

  "DELETE /pipelines/:id" should {

    "return success response from delete pipeline" in new Setup {
      pipelineService.delete("pipelineId") shouldReturn future(Right(()))
      Delete("/pipelines/pipelineId").signed.check {
        status shouldEqual StatusCodes.OK
        responseAs[JsObject] shouldBe JsObject(Seq("id" -> JsString("pipelineId")))
      }
    }

    "return error response from delete pipeline when pipeline does not exist" in new Setup {
      pipelineService.delete("pipelineId") shouldReturn future(Left(PipelineNotFound))
      Delete("/pipelines/pipelineId").signed.check {
        status shouldBe StatusCodes.NotFound
        validateErrorResponse(responseAs[JsObject])
      }
    }
  }

}
