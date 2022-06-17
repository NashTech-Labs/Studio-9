package baile.routes.dcproject

import java.time.Instant

import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.http.scaladsl.model.{ ContentTypes, StatusCodes }
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.unmarshalling.PredefinedFromEntityUnmarshallers.stringUnmarshaller
import baile.RandomGenerators._
import baile.daocommons.WithId
import baile.domain.common.Version
import baile.domain.dcproject.{ DCProject, DCProjectPackage, DCProjectPackageArtifact, DCProjectStatus }
import baile.routes.ExtendedRoutesSpec
import baile.services.usermanagement.util.TestData.SampleUser
import baile.services.dcproject.DCProjectPackageService
import baile.services.dcproject.DCProjectPackageService.{
  DCProjectPackageServiceCreateError,
  DCProjectPackageServiceError,
  ExtendedPackageResponse
}
import baile.services.dcproject.DCProjectPackageService.DCProjectPackageServiceError.DCProjectPackageNotFound
import cats.implicits._
import org.scalatest.prop.TableDrivenPropertyChecks
import play.api.libs.json._

class DCProjectPackageRoutesSpec extends ExtendedRoutesSpec with TableDrivenPropertyChecks {

  trait Setup extends RoutesSetup { self =>

    val service: DCProjectPackageService = mock[DCProjectPackageService]
    val routes: Route = new DCProjectPackageRoutes(conf, authenticationService, service).routes

    val dateTime = Instant.now()
    val dcProjectId = randomString()
    val packageName = randomString()
    val version = Version(1, 1, 1, None)
    val project = WithId(
      DCProject(
        name = "name",
        created = dateTime,
        updated = dateTime,
        ownerId = SampleUser.id,
        status = DCProjectStatus.Building,
        description = None,
        basePath = "/project1/",
        packageName = Some(packageName),
        latestPackageVersion = Some(version)
      ), dcProjectId
    )

    val dcProjectPackage = WithId(
      DCProjectPackage(
        ownerId = Some(user.id),
        dcProjectId = Some(dcProjectId),
        name = packageName,
        version = Some(version),
        location = Some(randomPath()),
        created = dateTime,
        description = None,
        isPublished = true
      ),
      randomString()
    )

    val packageResponseData: JsObject = Json.obj(
      "id" -> JsString(dcProjectPackage.id),
      "name" -> JsString(dcProjectPackage.entity.name),
      "created" -> JsString(dateTime.toString),
      "ownerId" -> dcProjectPackage.entity.ownerId.map(id => JsString(id.toString)),
      "version" -> dcProjectPackage.entity.version.map(x => JsString(x.toString)),
      "location" -> dcProjectPackage.entity.location.map(JsString),
      "dcProjectId" -> dcProjectPackage.entity.dcProjectId.map(JsString),
      "isPublished" -> JsBoolean(dcProjectPackage.entity.isPublished)
    )

    val extendedPackageResponseData: JsObject = Json.obj(
      "id" -> JsString(dcProjectPackage.id),
      "name" -> JsString(dcProjectPackage.entity.name),
      "created" -> JsString(dateTime.toString),
      "ownerId" -> dcProjectPackage.entity.ownerId.map(id => JsString(id.toString)),
      "version" -> dcProjectPackage.entity.version.map(x => JsString(x.toString)),
      "location" -> dcProjectPackage.entity.location.map(JsString),
      "dcProjectId" -> dcProjectPackage.entity.dcProjectId.map(JsString),
      "pipelineOperators" -> JsArray(),
      "primitives" -> JsArray(),
      "isPublished"-> JsBoolean(dcProjectPackage.entity.isPublished)
    )

    val projectResponseData: JsObject = Json.obj(
      "id" -> JsString(dcProjectId),
      "ownerId" -> JsString(project.entity.ownerId.toString),
      "name" -> JsString(project.entity.name),
      "status" -> JsString("BUILDING"),
      "created" -> JsString(dateTime.toString),
      "updated" -> JsString(dateTime.toString),
      "packageName" -> JsString(packageName),
      "packageVersion" -> JsString(version.toString)
    )

    val publishPackageResponseData: JsObject = Json.obj(
      "id" -> JsString(dcProjectPackage.id),
      "name" -> JsString(dcProjectPackage.entity.name),
      "created" -> JsString(dateTime.toString),
      "ownerId" -> dcProjectPackage.entity.ownerId.map(id => JsString(id.toString)),
      "version" -> dcProjectPackage.entity.version.map(x => JsString(x.toString)),
      "location" -> dcProjectPackage.entity.location.map(JsString),
      "dcProjectId" -> dcProjectPackage.entity.dcProjectId.map(JsString),
      "isPublished" -> JsBoolean(dcProjectPackage.entity.isPublished),
      "primitives" -> JsArray(),
      "pipelineOperators" -> JsArray()
    )

    val password = "password"
  }

  "POST /dc-projects/{projectId}/build endpoint" should {
    "build project and return updated project" in new Setup {
      service.create(
        *,
        *,
        *,
        *,
        *
      )(*) shouldReturn future(project.asRight)
      val requestJson =
        """
          |{
          |"name": "packageName",
          |"version": "1.1.1"
          |}
        """.stripMargin
      Post("/dc-projects/projectId/build", Json.parse(requestJson)).signed.check {
        status shouldBe StatusCodes.OK
        responseAs[JsObject] shouldBe projectResponseData
      }
    }

    "return error response" in new Setup {
      service.create(
        *,
        *,
        *,
        *,
        *
      )(*) shouldReturn future(Left(DCProjectPackageServiceCreateError.PackageNameIsRequired))

      val requestJson =
        """
          |{
          |"version": "1.1.1"
          |}
        """.stripMargin
      Post("/dc-projects/projectId/build", Json.parse(requestJson)).signed.check {
        status shouldBe StatusCodes.BadRequest
        responseAs[JsObject].keys should contain allOf("code", "message")
      }
    }
  }

  "POST /packages/:id/publish endpoint" should {
    "publish project and return project" in new Setup {
      service.publish(
        *,
        *
      )(*) shouldReturn future(ExtendedPackageResponse(dcProjectPackage, Seq(), Seq()).asRight)
      service.signPackage(*) shouldReturn dcProjectPackage
      val requestJson =
        """
          |{
          |"pipelineOperators": [{
          |"id":"operatorId",
          |"categoryId":"categoryId"
          |}]
          |}
        """.stripMargin
      Post(s"/packages/${ dcProjectPackage.id }/publish", Json.parse(requestJson)).signed.check {
        status shouldBe StatusCodes.OK
        responseAs[JsObject] shouldBe publishPackageResponseData
      }
    }

    "return error response" in new Setup {
      service.publish(
        *,
        *
      )(*) shouldReturn future(Left(DCProjectPackageServiceError.CategoryNotFound("categoryid")))
      val requestJson =
        """
          |{
          |"pipelineOperators": [{
          |"id":"operatorId",
          |"categoryId":"categoryid"
          |}]
          |}
        """.stripMargin
      Post(s"/packages/${ dcProjectPackage.id }/publish", Json.parse(requestJson)).signed.check {
        status shouldBe StatusCodes.BadRequest
        responseAs[JsObject].keys should contain allOf("code", "message")
      }
    }
  }

  "GET /packages endpoint" should {
    "return success response" in new Setup {
      service.list(
        *,
        *,
        *,
        *,
        *,
        *
      )(*) shouldReturn future((Seq(dcProjectPackage), 1).asRight)
      service.signPackage(any[WithId[DCProjectPackage]]) shouldReturn dcProjectPackage
      Get("/packages?page=1&page_size=1").signed.check {
        status shouldBe StatusCodes.OK
        val response = responseAs[JsObject]
        response.keys should contain allOf("data", "count")
        (response \ "count").as[Int] shouldBe 1
      }
    }

    "return error response when sorting field is unknown" in new Setup {
      service.list(
        *,
        *,
        *,
        *,
        *,
        *
      )(*) shouldReturn {
        future(Left(DCProjectPackageServiceError.SortingFieldUnknown))
      }
      Get("/packages?page=1&page_size=1").signed.check {
        status shouldBe StatusCodes.BadRequest
        responseAs[JsObject].keys should contain allOf("code", "message")
      }
    }
  }

  "GET /packages/:id endpoint" should {
    "return success response" in new Setup {
      service.getPackageWithPipelineOperators(eqTo(dcProjectPackage.id)) shouldReturn
        future(Right(ExtendedPackageResponse(dcProjectPackage, Seq(), Seq())))
      service.signPackage(*) shouldReturn dcProjectPackage
      Get(s"/packages/${ dcProjectPackage.id }").signed.check {
        status shouldBe StatusCodes.OK
        responseAs[JsObject] shouldBe extendedPackageResponseData
      }
    }

    "return error response when user does not has access" in new Setup {
      service.getPackageWithPipelineOperators(eqTo(dcProjectPackage.id)) shouldReturn
        future(Left(DCProjectPackageServiceError.AccessDenied))
      Get(s"/packages/${ dcProjectPackage.id }").signed.check {
        status shouldBe StatusCodes.Forbidden
        responseAs[JsObject].keys should contain allOf("code", "message")
      }
    }
  }

  "DELETE /packages/:id endpoint" should {
    "return success response" in new Setup {
      service.delete(*)(*) shouldReturn future(Right(()))
      Delete("/packages/id").signed.check {
        status shouldBe StatusCodes.OK
        responseAs[JsObject] shouldBe JsObject(Seq("id" -> JsString("id")))
      }
    }

    "return error response when user does not has access" in new Setup {
      service.delete(*)(*) shouldReturn future(Left(DCProjectPackageServiceError.AccessDenied))
      Delete("/packages/id").signed.check {
        status shouldBe StatusCodes.Forbidden
        responseAs[JsObject].keys should contain allOf("code", "message")
      }
    }
  }

  "GET /packages-index endpoint" should {
    "return html page with all packages" in new Setup {
      val packageNames = Seq("click", "dataclasses")
      val htmlPage = "<!DOCTYPE html>" +
        "<html>" +
        "<head><title>DC packages index</title></head>" +
        "<body>" +
        "<a href=\"/click\">click</a>" +
        "<a href=\"/dataclasses\">dataclasses</a>" +
        "</body>" +
        "</html>"
      service.listPackageNames(user) shouldReturn future(packageNames)
      authenticationService.authenticate(userToken) isLenient()
      authenticationService.authenticate(user.username, password) shouldReturn future(Some(SampleUser))

      forAll(Table(
        "path",
        "/packages-index/",
        "/packages-index/index.html"
      )) { path =>
        Get(path).withCredentials(BasicHttpCredentials(user.username, password))
          .check(ContentTypes.`text/html(UTF-8)`) {
            status shouldBe StatusCodes.OK
            responseAs[String] shouldBe htmlPage
          }
      }
    }
  }

  "GET /packages-index/:packageName endpoint" should {
    "return html page with all package artifacts" in new Setup {
      val artifacts = Seq(
        DCProjectPackageArtifact("package-1.0.0.whl", "http://s3/package-1.0.0.whl?signed"),
        DCProjectPackageArtifact("package-2.3.7.whl", "http://s3/package-2.3.7.whl?signed")
      )
      val htmlPage = "<!DOCTYPE html>" +
        "<html>" +
        s"<head><title>Links for $packageName</title></head>" +
        "<body>" +
        "<a href=\"http://s3/package-1.0.0.whl?signed\">package-1.0.0.whl</a>" +
        "<a href=\"http://s3/package-2.3.7.whl?signed\">package-2.3.7.whl</a>" +
        "</body>" +
        "</html>"
      service.listPackageArtifacts(packageName)(user) shouldReturn future(artifacts.asRight)
      authenticationService.authenticate(userToken) isLenient()
      authenticationService.authenticate(user.username, password) shouldReturn future(Some(SampleUser))

      forAll(Table(
        "path",
        s"/packages-index/$packageName/",
        s"/packages-index/$packageName/index.html"
      )) { path =>
        Get(path)
          .withCredentials(BasicHttpCredentials(user.username, password))
          .check(ContentTypes.`text/html(UTF-8)`) {
            status shouldBe StatusCodes.OK
            responseAs[String] shouldBe htmlPage
          }
      }
    }

    "return error if package is not found" in new Setup {
      service.listPackageArtifacts(packageName)(user) shouldReturn future(DCProjectPackageNotFound.asLeft)
      authenticationService.authenticate(userToken) isLenient()
      authenticationService.authenticate(user.username, password) shouldReturn future(Some(SampleUser))

      forAll(Table(
        "path",
        s"/packages-index/$packageName/",
        s"/packages-index/$packageName/index.html"
      )) { path =>
        Get(path)
          .withCredentials(BasicHttpCredentials(user.username, password))
          .check(ContentTypes.`text/html(UTF-8)`) {
            status shouldBe StatusCodes.NotFound
            responseAs[String] should startWith("<!DOCTYPE html>")
            responseAs[String] should include("<html>")
          }
      }
    }
  }

}
