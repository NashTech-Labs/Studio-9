package baile.routes.tabular.model

import java.time.Instant

import akka.http.scaladsl.model.{ ContentTypes, HttpEntity, Multipart, StatusCodes }
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import baile.daocommons.WithId
import baile.domain.common.{ ClassReference, Version }
import baile.domain.dcproject.DCProjectPackage
import baile.domain.table.{ ColumnDataType, ColumnVariableType }
import baile.domain.tabular.model.{ ModelColumn, TabularModel, TabularModelStatus }
import baile.domain.usermanagement.User
import baile.routes.RoutesSpec
import baile.services.common.{ AuthenticationService, FileUploadService }
import baile.services.remotestorage.S3StorageService
import baile.services.tabular.model.TabularModelService
import baile.services.tabular.model.TabularModelService.{
  TabularModelImportError,
  TabularModelServiceError
}
import baile.services.usermanagement.util.TestData.SampleUser
import cats.implicits._
import org.mockito.ArgumentMatchers.{ any, eq => eqTo }
import org.mockito.Mockito._
import play.api.libs.json.{ JsArray, JsBoolean, JsObject, JsString, Json }

import scala.concurrent.Future

class TabularModelRoutesSpec extends RoutesSpec {

  private val service = mock[TabularModelService]
  private val authenticationService = mock[AuthenticationService]
  private val s3StorageService = mock[S3StorageService]
  private val fileUploadService = new FileUploadService(s3StorageService, "prefix")
  private val appUrl = randomString()

  val routes: Route = new TabularModelRoutes(conf, service, authenticationService, fileUploadService, appUrl).routes
  private implicit val user: User = SampleUser
  private val dCProjectPackageWithId = WithId(
    DCProjectPackage(
      ownerId = Some(user.id),
      dcProjectId = Some(randomString()),
      name = randomString(),
      version = Some(Version(0, 1, 0, None)),
      location = Some(randomPath()),
      created = Instant.now(),
      description = None,
      isPublished = true
    ),
    randomString()
  )
  private val model =
    WithId(
      TabularModel(
        ownerId = SampleUser.id,
        name = "name",
        predictorColumns = Seq.empty,
        responseColumn = ModelColumn("column", "column", ColumnDataType.Timestamp, ColumnVariableType.Continuous),
        classNames = None,
        classReference = ClassReference(
          moduleName = randomString(),
          className = randomString(),
          packageId = dCProjectPackageWithId.id
        ),
        cortexModelReference = None,
        inLibrary = true,
        status = TabularModelStatus.Active,
        created = Instant.now,
        updated = Instant.now,
        description = None,
        experimentId = None
      ),
      "m"
    )
  private val name = randomString()
  private val tabularModelSaveRequestJson = Json.parse(
    s"""{
      |"name": "$name"
      |}
    """.stripMargin)

  private val tabularModelSaveResponse = Json.obj(
    "id" -> JsString(model.id),
    "ownerId" -> JsString(model.entity.ownerId.toString),
    "name" -> JsString(model.entity.name),
    "status" -> JsString("ACTIVE"),
    "created" -> JsString(model.entity.created.toString),
    "updated" -> JsString(model.entity.updated.toString),
    "class" -> JsString("REGRESSION"),
    "classReference" -> Json.obj(
      "packageId" -> JsString(model.entity.classReference.packageId),
      "moduleName" -> JsString(model.entity.classReference.moduleName),
      "className" -> JsString(model.entity.classReference.className)
    ),
    "responseColumn" -> Json.obj(
      "name" -> JsString("column"),
      "displayName" -> JsString("column"),
      "dataType" -> JsString("TIMESTAMP"),
      "variableType" -> JsString("CONTINUOUS")
    ),
    "predictorColumns" -> JsArray.empty,
    "inLibrary" -> JsBoolean(true)
  )

  when(authenticationService.authenticate(userToken)).thenReturn(future(Some(SampleUser)))
  when(service.get("2", Some("123"))) thenReturn future(TabularModelServiceError.ModelNotFound.asLeft)
  when(service.get("1", Some("1234"))) thenReturn future(model.asRight)
  when(service.list(None, None, Seq(), 1, 1, None, None)) thenReturn future((Seq(model), 1).asRight)
  when(service.list(None, None, Seq(), 1, 2, None, None)) thenReturn future(
    TabularModelServiceError.AccessDenied.asLeft
  )
  when(service.delete("xyz")) thenReturn future(TabularModelServiceError.ModelNotFound.asLeft)
  when(service.delete("id")) thenReturn future(().asRight)
  when(service.update("id",Some("newName"), None)) thenReturn future(model.asRight)
  when(service.update("id", Some(""), None)) thenReturn future(TabularModelServiceError.ModelNameIsEmpty.asLeft)
  when(service.getStateFileUrl("id")) thenReturn future(Right("s3://"))
  when(service.update(
    "id",
    Some("xyz"),
    None
  )) thenReturn future(TabularModelServiceError.ModelNameAlreadyExists.asLeft)
  when(service.save("id", name, None)) thenReturn future(model.asRight)
  when(service.export(
    eqTo("id")
  )(any[User]))
    .thenReturn(future(Right(Source.empty)))

  "POST /models/:id/save" should {
    "return 200 response with TabularModel list" in {
      Post("/models/id/save", tabularModelSaveRequestJson).signed.check {
        status shouldEqual StatusCodes.OK
        responseAs[JsObject] shouldBe tabularModelSaveResponse
      }
    }

    "return 400 response" in {
      when(service.save("id", name, None)) thenReturn future(TabularModelServiceError.EmptyTabularModelName.asLeft)
      Post("/models/id/save", tabularModelSaveRequestJson).signed.check {
        status shouldBe StatusCodes.BadRequest
        validateErrorResponse(responseAs[JsObject])
      }
    }

  }

  "GET /models/:id/state-file-url endpoint" should {

    "get state file url for model" in {
      Get("/models/id/state-file-url").signed.check {
        status shouldEqual StatusCodes.OK
      }
    }

    "get error if no state file url for model" in {
      when(service.getStateFileUrl("id")) thenReturn future(Left(TabularModelServiceError.ModelFilePathNotFound))
      Get("/models/id/state-file-url").signed.check {
        status shouldBe StatusCodes.BadRequest
      }
    }

    "get error if model is not in active state" in {
      when(service.getStateFileUrl("id")) thenReturn future(Left(TabularModelServiceError.ModelNotActive))
      Get("/models/id/state-file-url").signed.check {
        status shouldBe StatusCodes.BadRequest
      }
    }
  }

  "GET /models" should {
    "return 200 response with TabularModel list" in {
      Get("/models?page_size=1").signed.check {
        status shouldEqual StatusCodes.OK
        val response = responseAs[JsObject]
        response.keys should contain allOf("data", "count")
        (response \ "count").as[Int] shouldBe 1
      }
    }

    "return 403 response" in {
      Get("/models?page_size=2").signed.check {
        status shouldBe StatusCodes.Forbidden
        validateErrorResponse(responseAs[JsObject])
      }
    }
  }

  "GET /models/:id" should {

    "get model" in {
      Get("/models/1?shared_resource_id=1234").signed.check {
        status shouldBe StatusCodes.OK
        validateTabularModelResponse(responseAs[JsObject])
      }
    }

    "get no model" in {
      Get("/models/2?shared_resource_id=123").signed.check {
        status shouldBe StatusCodes.NotFound
        validateErrorResponse(responseAs[JsObject])
      }
    }

  }


  "DELETE /models/:id" should {

    "return 200 response" in {
      Delete("/models/id").signed.check {
        status shouldBe StatusCodes.OK
        responseAs[JsObject] shouldBe JsObject(Seq("id" -> JsString("id")))
      }
    }

    "return 404 response" in {
      Delete("/models/xyz").signed.check {
        status shouldBe StatusCodes.NotFound
        validateErrorResponse(responseAs[JsObject])
      }
    }
  }

  "PUT /models/:id" should {

    "return 200 response" in {
      Put("/models/id", Json.parse({ """{"name":"newName" }""" })).signed.check {
        status shouldBe StatusCodes.OK
        validateTabularModelResponse(responseAs[JsObject])
      }
    }

    "return 400 response when model name is empty" in {
      Put("/models/id", Json.parse({ """{"name":""}""" })).signed.check {
        status shouldBe StatusCodes.BadRequest
        validateErrorResponse(responseAs[JsObject])
      }
    }

    "return 400 response when model name already exists" in {
      Put("/models/id", Json.parse({ """{"name":"xyz"}""" })).signed.check {
        status shouldBe StatusCodes.BadRequest
        validateErrorResponse(responseAs[JsObject])
      }
    }
  }

  "GET /models/{id}/export endpoint" should {
    "get uri for model file path" in {
      Get("/models/id/export").signed.check {
        status shouldBe StatusCodes.OK
      }
    }
    "get no uri for inactive model" in {
      when(service.export(
        eqTo("bad-id")
      )(any[User])).thenReturn(future(Left(TabularModelServiceError.CantExportTabularModel)))
      Get("/models/bad-id/export").signed.check {
        status shouldBe StatusCodes.BadRequest
      }
    }
  }

  "GET /models/{id}/exportFile endpoint" should {
    "get uri for model file path" in {
      Get("/models/id/exportFile").withQuery("access_token" -> userToken)
        .check(ContentTypes.`application/octet-stream`) {
          status shouldBe StatusCodes.OK
        }
    }
    "require authentication" in {
      Get("/models/bad-id/exportFile").check {
        status shouldBe StatusCodes.Unauthorized
      }
    }
    "get no uri for model file path" in {
      when(service.export(
        eqTo("bad-id")
      )(any[User])).thenReturn(future(Left(TabularModelServiceError.ModelNotActive)))
      Get("/models/bad-id/exportFile").withQuery("access_token" -> userToken).check {
        status shouldBe StatusCodes.BadRequest
      }
    }
  }

  "POST /models/import endpoint" should {

    "return success response" in {
      when(service.importModel(
        any[Source[ByteString, Any]],
        any[Future[Map[String, String]]],
      )(any[User], any[Materializer])).thenReturn(future(model.asRight))

      val name = Multipart.FormData.BodyPart.Strict("name", "xyz")
      val fileData = Multipart.FormData.BodyPart.Strict(
        "file", HttpEntity(ContentTypes.`text/plain(UTF-8)`, "This is a test file"), Map("fileName" -> "abc.bin"))
      val formData = Multipart.FormData(name, fileData)
      Post("/models/import", formData).signed.check {
        status shouldEqual StatusCodes.OK
      }
    }

    "return error response when service returns an error" in {
      when(service.importModel(
        any[Source[ByteString, Any]],
        any[Future[Map[String, String]]],
      )(any[User], any[Materializer])).thenReturn(future(TabularModelImportError.NameIsTaken.asLeft))

      val name = Multipart.FormData.BodyPart.Strict("name", "xyz")
      val fileData = Multipart.FormData.BodyPart.Strict(
        "file", HttpEntity(ContentTypes.`text/plain(UTF-8)`, "This is a test file"), Map("fileName" -> "abc.csv"))
      val formData = Multipart.FormData(name, fileData)
      Post("/models/import", formData).signed.check {
        status shouldBe StatusCodes.BadRequest
        validateErrorResponse(responseAs[JsObject])
      }
    }
  }

  private def validateTabularModelResponse(response: JsObject): Unit = {
    response.fields should contain allOf(
      "id" -> JsString("m"),
      "ownerId" -> JsString(model.entity.ownerId.toString),
      "name" -> JsString(model.entity.name),
      "status" -> JsString(model.entity.status.toString.toUpperCase()),
      "created" -> JsString(model.entity.created.toString),
      "updated" -> JsString(model.entity.updated.toString),
      "class" -> JsString("REGRESSION"),
      "classReference" -> Json.obj(
        "packageId" -> JsString(model.entity.classReference.packageId),
        "moduleName" -> JsString(model.entity.classReference.moduleName),
        "className" -> JsString(model.entity.classReference.className)
      ),
      "predictorColumns" -> JsArray()
    )
  }

}
