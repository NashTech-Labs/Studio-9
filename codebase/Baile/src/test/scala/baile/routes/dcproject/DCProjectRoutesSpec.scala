package baile.routes.dcproject

import java.time.Instant

import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.model.headers.CacheDirectives.`no-cache`
import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.Source
import akka.util.ByteString
import baile.daocommons.WithId
import baile.domain.dcproject.{ DCProject, DCProjectStatus }
import baile.routes.ExtendedRoutesSpec
import baile.services.usermanagement.util.TestData.SampleUser
import baile.services.dcproject.DCProjectService
import baile.services.dcproject.DCProjectService.DCProjectServiceError
import baile.services.remotestorage.{ Directory, File }
import cats.implicits._
import play.api.libs.json.{ JsObject, JsString, Json }

class DCProjectRoutesSpec extends ExtendedRoutesSpec {

  trait Setup extends RoutesSetup {
    val service: DCProjectService = mock[DCProjectService]
    val routes: Route = new DCProjectRoutes(conf, authenticationService, service).routes

    val dateTime = Instant.now()
    val entity = WithId(
      DCProject(
        name = "name",
        created = dateTime,
        updated = dateTime,
        ownerId = SampleUser.id,
        status = DCProjectStatus.Idle,
        description = None,
        basePath = "/project1/",
        packageName = None,
        latestPackageVersion = None
      ), "id"
    )
    val projectResponseData: JsObject = Json.obj(
      "id" -> JsString("id"),
      "ownerId" -> JsString(entity.entity.ownerId.toString),
      "name" -> JsString(entity.entity.name),
      "status" -> JsString("IDLE"),
      "created" -> JsString(dateTime.toString),
      "updated" -> JsString(dateTime.toString)
    )

  }

  "GET /dc-projects endpoint" should {
    "return list of dc projects" in new Setup {
      service.list(
        *,
        *,
        *,
        *,
        *,
        *,
        *
      )(*) shouldReturn future((Seq(entity), 1).asRight)
      Get("/dc-projects?page=1&page_size=1").signed.check {
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
        *,
        *
      )(*) shouldReturn {
        future(Left(DCProjectServiceError.SortingFieldUnknown))
      }
      Get("/dc-projects?page=1&page_size=1").signed.check {
        status shouldBe StatusCodes.BadRequest
        responseAs[JsObject].keys should contain allOf("code", "message")
      }
    }
  }

  "GET /dc-projects/:id endpoint" should {
    "return dc project" in new Setup {
      service.get(*, *)(*) shouldReturn future(Right(entity))
      Get("/dc-projects/id").signed.check {
        status shouldBe StatusCodes.OK
        responseAs[JsObject] shouldBe projectResponseData
      }
    }

    "return error response when user does not has access" in new Setup {
      service.get(*, *)(*) shouldReturn
        future(Left(DCProjectServiceError.AccessDenied))
      Get("/dc-projects/id").signed.check {
        status shouldBe StatusCodes.Forbidden
        responseAs[JsObject].keys should contain allOf("code", "message")
      }
    }
  }

  "DELETE /dc-projects/:id endpoint" should {
    "delete dc-project" in new Setup {
      service.delete(*)(*) shouldReturn future(Right(()))
      Delete("/dc-projects/id").signed.check {
        status shouldBe StatusCodes.OK
        responseAs[JsObject] shouldBe JsObject(Seq("id" -> JsString("id")))
      }
    }

    "return error response when user does not has access" in new Setup {
      service.delete(*)(*) shouldReturn future(Left(DCProjectServiceError.AccessDenied))
      Delete("/dc-projects/id").signed.check {
        status shouldBe StatusCodes.Forbidden
        responseAs[JsObject].keys should contain allOf("code", "message")
      }
    }
  }

  "POST /dc-projects endpoint" should {

    "create dc-project" in new Setup {
      service.create(
        *,
        *
      )(*) shouldReturn future(entity.asRight)

      val requestJson =
        """
          |{
          |"name": "name",
          |"description": "description"
          |}
        """.stripMargin
      Post("/dc-projects", Json.parse(requestJson)).signed.check {
        status shouldBe StatusCodes.OK
        responseAs[JsObject] shouldBe projectResponseData
      }
    }

    "return error response when project name is empty" in new Setup {
      service.create(
        *,
        *
      )(*) shouldReturn {
        future(
          DCProjectServiceError.EmptyProjectName.asLeft
        )
      }
      val requestJson =
        """
          |{
          |"name": "",
          |"description": "description"
          |}
        """.stripMargin
      Post("/dc-projects", Json.parse(requestJson)).signed.check {
        status shouldBe StatusCodes.BadRequest
        responseAs[JsObject].keys should contain allOf("code", "message", "errors")
      }
    }

  }

  "PUT /dc-projects/:id endpoint" should {

    "update dc project name" in new Setup {
      service.update(
        "id",
        Some("newName"),
        None
      ) shouldReturn future(entity.asRight)

      Put("/dc-projects/id", Json.parse("""{"name":"newName"}""")).signed.check {
        status shouldBe StatusCodes.OK
        responseAs[JsObject] shouldBe projectResponseData
      }
    }

    "return error response when new name is empty" in new Setup {
      service.update(
        "id",
        Some(""),
        None
      ) shouldReturn future(DCProjectServiceError.EmptyProjectName.asLeft)

      Put("/dc-projects/id", Json.parse("""{"name":""}""")).signed.check {
        status shouldBe StatusCodes.BadRequest
        responseAs[JsObject].keys should contain allOf("code", "message", "errors")
      }
    }

  }

  val directoryContentType = ContentType.parse("application/x-directory").getOrElse(
    throw new RuntimeException("Could not parse application/x-directory content type")
  )

  "PUT /dc-projects/:id/files/:filePath" should {

    "move a file" in new Setup {
      val file = File("file1", 256L, Instant.now)
      val fileResponseData: JsObject = Json.obj(
        "type" -> "FILE",
        "name" -> file.path,
        "modified" -> file.lastModified
      )
      service.moveFile("id", "file1", "file2") shouldReturn future(file.asRight)
      Put("/dc-projects/id/files/file2", "")
        .addHeader(RawHeader("x-move-source", "file1"))
        .signed
        .check {
          status shouldBe StatusCodes.OK
          responseAs[JsObject] shouldBe fileResponseData
        }
    }

    "return error response while moving file, if Project is not in Idle mode" in new Setup {
     service.moveFile(
        "id",
        "file1",
        "file2"
      ) shouldReturn future(DCProjectServiceError.ProjectIsNotInIdleMode.asLeft)
      Put("/dc-projects/id/files/file2", "")
        .addHeader(RawHeader("x-move-source", "file1"))
        .signed
        .check {
          status shouldBe StatusCodes.BadRequest
          validateErrorResponse(responseAs[JsObject])
        }
    }

    "copy a file" in new Setup {
      val file = File("file1", 256L, Instant.now)
      val fileResponseData: JsObject = Json.obj(
        "type" -> "FILE",
        "name" -> file.path,
        "modified" -> file.lastModified
      )
      service.copyFile("id", "file1", "file2") shouldReturn future(file.asRight)
      Put("/dc-projects/id/files/file2", "")
        .addHeader(RawHeader("x-copy-source", "file1"))
        .signed
        .check {
          status shouldBe StatusCodes.OK
          responseAs[JsObject] shouldBe fileResponseData
        }
    }

    "return error response while copying file, if project is not in Idle mode" in new Setup {
      service.copyFile(
        "id",
        "file1",
        "file2"
      ) shouldReturn future(DCProjectServiceError.ProjectIsNotInIdleMode.asLeft)
      Put("/dc-projects/id/files/file2", "")
        .addHeader(RawHeader("x-copy-source", "file1"))
        .signed
        .check {
          status shouldBe StatusCodes.BadRequest
          validateErrorResponse(responseAs[JsObject])
        }
    }

    "update a file" in new Setup {
      val file = File("file1", 256L, Instant.now)
      val fileResponseData: JsObject = Json.obj(
        "type" -> "FILE",
        "name" -> file.path,
        "modified" -> file.lastModified
      )
      service.updateFile(
        eqTo("id"),
        eqTo("file1"),
        *,
        *
      )(eqTo(user), *) shouldReturn future(file.asRight)
      Put("/dc-projects/id/files/file1", "content")
        // Ideally, it should be as commented. But akka does not reparse raw headers to
        // get instances of predefined headers like `If-Unmodified-Since`
        //        .addHeader(RawHeader("if-unmodified-since", DateTime(file.lastModified.toEpochMilli).toRfc1123DateTimeString()))
        .addHeader(`If-Unmodified-Since`(DateTime(file.lastModified.toEpochMilli)))
        .signed
        .check {
          status shouldBe StatusCodes.OK
          responseAs[JsObject] shouldBe fileResponseData
        }
    }

    "create a new file" in new Setup {
      val file = File("file1", 256L, Instant.now)
      val fileResponseData: JsObject = Json.obj(
        "type" -> "FILE",
        "name" -> file.path,
        "modified" -> file.lastModified
      )
      service.createFile(
        eqTo("id"),
        eqTo("file1"),
        *
      )(eqTo(user), *) shouldReturn future(file.asRight)
      Put("/dc-projects/id/files/file1", "content").signed.check {
        status shouldBe StatusCodes.OK
        responseAs[JsObject] shouldBe fileResponseData
      }
    }

    "return error response, if Project is not in Idle mode" in new Setup {
      val file = File("file1", 256L, Instant.now)
      service.updateFile(
        eqTo("id"),
        eqTo("file1"),
        *,
        *
      )(eqTo(user), *) shouldReturn future(DCProjectServiceError.ProjectIsNotInIdleMode.asLeft)
      Put("/dc-projects/id/files/file1", "content")
        .addHeader(`If-Unmodified-Since`(DateTime(file.lastModified.toEpochMilli)))
        .signed
        .check {
          status shouldBe StatusCodes.BadRequest
          validateErrorResponse(responseAs[JsObject])
        }
    }

    "create new folder" in new Setup {
      val directory = Directory("path")
      val directoryResponseData: JsObject = Json.obj(
        "type" -> "DIR",
        "name" -> directory.path
      )
      service.createFolder(
        "id",
        "folder1/subfolder1"
      ) shouldReturn future(directory.asRight)
      Put(
        "/dc-projects/id/files/folder1/subfolder1",
        HttpEntity(directoryContentType, Array.empty[Byte])
      ).signed.check {
        status shouldBe StatusCodes.OK
        responseAs[JsObject] shouldBe directoryResponseData
      }
    }

    "return error response while creating folder, if Project is not in Idle mode " in new Setup {
      service.createFolder(
        "id",
        "folder1/subfolder1"
      ) shouldReturn future(DCProjectServiceError.ProjectIsNotInIdleMode.asLeft)
      Put(
        "/dc-projects/id/files/folder1/subfolder1",
        HttpEntity(directoryContentType, Array.empty[Byte])
      ).signed.check {
        status shouldBe StatusCodes.BadRequest
        validateErrorResponse(responseAs[JsObject])
      }
    }

    "move folder" in new Setup {
      val directory = Directory("path")
      val directoryResponseData: JsObject = Json.obj(
        "type" -> "DIR",
        "name" -> directory.path
      )
      service.moveFolder(
        "id",
        "folder1",
        "folder2"
      ) shouldReturn future(directory.asRight)
      Put("/dc-projects/id/files/folder2", HttpEntity(directoryContentType, Array.empty[Byte]))
        .addHeader(RawHeader("x-move-source", "folder1"))
        .signed
        .check {
          status shouldBe StatusCodes.OK
          responseAs[JsObject] shouldBe directoryResponseData
        }
    }

    "return error response while moving folder, if Project is not in Idle mode" in new Setup {
      service.moveFolder(
        "id",
        "folder1",
        "folder2"
      ) shouldReturn future(DCProjectServiceError.ProjectIsNotInIdleMode.asLeft)
      Put("/dc-projects/id/files/folder2", HttpEntity(directoryContentType, Array.empty[Byte]))
        .addHeader(RawHeader("x-move-source", "folder1"))
        .signed
        .check {
          status shouldBe StatusCodes.BadRequest
          validateErrorResponse(responseAs[JsObject])
        }
    }

    "return error response if object already exists" in new Setup {
      service.createFolder(
        "id",
        "folder1"
      ) shouldReturn future(DCProjectServiceError.ObjectAlreadyExists.asLeft)
      Put("/dc-projects/id/files/folder1", HttpEntity(directoryContentType, Array.empty[Byte])).signed.check {
        status shouldBe StatusCodes.Conflict
        responseAs[JsObject].keys should contain allOf("code", "message", "errors")
      }
    }

  }

  "DELETE /dc-projects/:id/files/:filePath" should {

    "remove object" in new Setup {
      service.removeObject(
        "id",
        "folder"
      ) shouldReturn future(().asRight)
      Delete("/dc-projects/id/files/folder").signed.check {
        status shouldBe StatusCodes.OK
      }
    }

    "return error response, if Project is not in Idle mode" in new Setup {
      service.removeObject(
        "id",
        "folder"
      ) shouldReturn future(DCProjectServiceError.ProjectIsNotInIdleMode.asLeft)
      Delete("/dc-projects/id/files/folder").signed.check {
        status shouldBe StatusCodes.BadRequest
        validateErrorResponse(responseAs[JsObject])
      }
    }

    "return error response if object does not exist" in new Setup {
      service.removeObject(
        "id",
        "folder"
      ) shouldReturn future(DCProjectServiceError.ObjectNotFound.asLeft)
      Delete("/dc-projects/id/files/folder").signed.check {
        status shouldBe StatusCodes.NotFound
      }
    }

  }

  "GET /dc-projects/:id/files/:filePath" should {
    val file = File("file1", 256L, Instant.now)

    "return source with file content" in new Setup {
      private val contentSource = Source[ByteString](List(ByteString("scala akka http { } { }")))
      service.getFile("id", "file1") shouldReturn future(file.asRight)
      service.getFileContent("id", "file1") shouldReturn future(contentSource.asRight)

      Get("/dc-projects/id/files/file1")
        .addHeader(`If-Modified-Since`(DateTime(0L)))
        .signed
        .check(ContentType(MediaTypes.`application/octet-stream`)) {
          status shouldBe StatusCodes.OK
          headers should contain allOf(
            `Last-Modified`(DateTime(file.lastModified.toEpochMilli)),
            `Cache-Control`(`no-cache`)
          )
        }
    }

    "not return file content if it hasn't been modified" in new Setup {
      service.getFile("id", "file1") shouldReturn future(file.asRight)

      Get("/dc-projects/id/files/file1")
        .addHeader(`If-Modified-Since`(DateTime(file.lastModified.toEpochMilli)))
        .signed.check(ContentTypes.NoContentType) {
          status shouldBe StatusCodes.NotModified
        }
    }

    "return error response if object does not exist (#getFile)" in new Setup {
      service.getFile("id", "file1") shouldReturn future(DCProjectServiceError.ObjectNotFound.asLeft)
      Get("/dc-projects/id/files/file1").signed.check {
        status shouldBe StatusCodes.NotFound
      }
    }

    "return error response if object does not exist (#getFileContent)" in new Setup {
      service.getFile("id", "file1") shouldReturn future(file.asRight)
      service.getFileContent("id", "file1") shouldReturn future(DCProjectServiceError.ObjectNotFound.asLeft)
      Get("/dc-projects/id/files/file1").signed.check {
        status shouldBe StatusCodes.NotFound
      }
    }
  }

}
