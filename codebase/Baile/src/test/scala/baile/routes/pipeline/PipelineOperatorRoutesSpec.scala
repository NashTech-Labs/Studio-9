package baile.routes.pipeline

import java.time.Instant

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import baile.RandomGenerators.randomString
import baile.daocommons.WithId
import baile.domain.common.Version
import baile.domain.dcproject.DCProjectPackage
import baile.domain.pipeline._
import baile.routes.ExtendedRoutesSpec
import baile.services.pipeline.PipelineOperatorService
import baile.services.pipeline.PipelineOperatorService.{ PipelineOperatorInfo, PipelineOperatorServiceError }
import play.api.libs.json.{ JsObject, JsString }

class PipelineOperatorRoutesSpec extends ExtendedRoutesSpec {

  trait Setup extends RoutesSetup { self =>

    val service: PipelineOperatorService = mock[PipelineOperatorService]

    val routes: Route = new PipelineOperatorRoutes(
      conf,
      authenticationService,
      service
    ).routes

    val operatorId = "pipelineOperatorId"
    val dcProjectPackage = WithId(
      DCProjectPackage(
        ownerId = Some(user.id),
        dcProjectId = Some(randomString()),
        name = "packageName",
        version = Some(Version(1, 0, 0, None)),
        location = Some("packageLocation"),
        created = Instant.now(),
        description = None,
        isPublished = true
      ),
      randomString()
    )
    val operator = WithId(
      PipelineOperator(
        name = "SCAE operator",
        description = Some("operator description"),
        category = Some("OTHER"),
        moduleName = "ml_lib.feature_extractors.backbones",
        className = "SCAE",
        packageId = dcProjectPackage.id,
        params = Seq(OperatorParameter(
          name = randomString(),
          description = None,
          multiple = false,
          typeInfo = StringParameterTypeInfo(
            values = Seq("a", "b"),
            default = Seq.empty
          ),
          conditions = Map.empty
        )),
        inputs = Seq(),
        outputs = Seq()
      ),
      operatorId
    )

    def validateResponse(operator: WithId[PipelineOperator], response: JsObject): Unit = {
      response.fields should contain allOf(
        "id" -> JsString(operator.id),
        "name" -> JsString(operator.entity.name),
        "moduleName" -> JsString(operator.entity.moduleName),
        "className" -> JsString(operator.entity.className)
      )
    }

  }

  "GET /pipeline-operators/:id" should {

    "return success response" in new Setup {
      service.getPipelineOperator(operatorId) shouldReturn
        future(Right(PipelineOperatorInfo(operator, dcProjectPackage)))
      Get(s"/pipeline-operators/$operatorId").signed.check {
        status shouldBe StatusCodes.OK
        validateResponse(operator, responseAs[JsObject])
      }
    }

    "return error response when pipeline not found" in new Setup {
      service.getPipelineOperator("pipelineId") shouldReturn
        future(Left(PipelineOperatorServiceError.PipelineOperatorNotFound))
      Get("/pipeline-operators/pipelineId").signed.check {
        status shouldBe StatusCodes.NotFound
        validateErrorResponse(responseAs[JsObject])
      }
    }

  }

  "GET /pipeline-operators" should {

    "return success response" in new Setup {
      service.list(*, *, *, *, *, *, *) shouldReturn
        future(Right((Seq(PipelineOperatorInfo(operator, dcProjectPackage)), 1)))
      Get("/pipeline-operators?page=1&page_size=1").signed.check {
        status shouldBe StatusCodes.OK
        val response = responseAs[JsObject]
        response.keys should contain allOf("data", "count")
        (response \ "count").as[Int] shouldBe 1
      }
    }

    "return success response when module name, class name, package name is passed" in new Setup {
      service.list(*, *, *, Some("moduleName"), Some("className"), Some("packageName"), *) shouldReturn
        future(Right((Seq(PipelineOperatorInfo(operator, dcProjectPackage)), 1)))
      Get("/pipeline-operators?page=1&page_size=1&moduleName=moduleName&className=className" +
        "&packageName=packageName").signed.check {
        status shouldBe StatusCodes.OK
        val response = responseAs[JsObject]
        response.keys should contain allOf("data", "count")
        (response \ "count").as[Int] shouldBe 1
      }
    }

    "return error response" in new Setup {
      service.list(*, *, *, Some("moduleName"), Some("className"), *, *) shouldReturn
        future(Left(PipelineOperatorServiceError.AccessDenied))
      Get("/pipeline-operators?page=1&page_size=1&moduleName=moduleName&className=className" +
        "&packageName=packageName").signed.check {
        status shouldBe StatusCodes.Forbidden
        validateErrorResponse(responseAs[JsObject])
      }
    }

  }

}
