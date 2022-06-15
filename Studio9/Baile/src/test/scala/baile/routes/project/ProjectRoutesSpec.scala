package baile.routes.project

import java.time.Instant

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import baile.daocommons.WithId
import baile.domain.project.{ Folder, Project }
import baile.routes.ExtendedRoutesSpec
import baile.services.cv.model.CVModelService
import baile.services.cv.prediction.CVPredictionService
import baile.services.dataset.DatasetService
import baile.services.dcproject.DCProjectService
import baile.services.experiment.ExperimentService
import baile.services.images.AlbumService
import baile.services.onlinejob.OnlineJobService
import baile.services.pipeline.PipelineService
import baile.services.project.ProjectService
import baile.services.project.ProjectService.ProjectServiceCreateError
import baile.services.project.ProjectService.ProjectServiceError._
import baile.services.table.TableService
import baile.services.tabular.model.TabularModelService
import baile.services.tabular.prediction.TabularPredictionService
import baile.services.usermanagement.util.TestData.SampleUser
import cats.implicits._
import play.api.libs.json._

class ProjectRoutesSpec extends ExtendedRoutesSpec {

  trait Setup extends RoutesSetup { self =>

    val projectService = mock[ProjectService]
    val albumService = mock[AlbumService]
    val cvModelService = mock[CVModelService]
    val cvPredictionService = mock[CVPredictionService]
    val onlineJobService = mock[OnlineJobService]
    val tableService = mock[TableService]
    val dcProjectService = mock[DCProjectService]
    val experimentService = mock[ExperimentService]
    val pipelineService = mock[PipelineService]
    val datasetService = mock[DatasetService]
    val tabularModelService = mock[TabularModelService]
    val tabularPredictionService = mock[TabularPredictionService]

    val routes: Route = new ProjectRoutes(
      conf,
      authenticationService,
      projectService,
      albumService,
      cvModelService,
      cvPredictionService,
      onlineJobService,
      tableService,
      dcProjectService,
      experimentService,
      pipelineService,
      datasetService,
      tabularModelService,
      tabularPredictionService
    ).routes

    def createProjectWithId(): WithId[Project] = {
      val dateTime = Instant.now()
      val projectId = "projectId"
      val project = Project(
        name = "name",
        ownerId = SampleUser.id,
        created = dateTime,
        updated = dateTime,
        folders = Seq.empty,
        assets = Seq.empty
      )

      WithId(project, projectId)
    }

    def validateExtendedProjectResponse(project: WithId[Project], response: JsObject): Unit = {
      response.fields should contain allOf(
        "id" -> JsString(project.id),
        "ownerId" -> JsString(project.entity.ownerId.toString),
        "name" -> JsString(project.entity.name),
        "modelsCount" -> JsNumber(0),
        "flowsCount" -> JsNumber(0),
        "tablesCount" -> JsNumber(0)
      )
      Instant.parse((response \ "created").as[String]) shouldBe project.entity.created
      Instant.parse((response \ "updated").as[String]) shouldBe project.entity.updated
    }

    def validateCreateProjectResponse(project: WithId[Project], response: JsObject): Unit = {
      response.fields should contain allOf(
        "id" -> JsString(project.id),
        "name" -> JsString(project.entity.name),
        "ownerId" -> JsString(project.entity.ownerId.toString)
      )
      Instant.parse((response \ "created").as[String]) shouldBe project.entity.created
      Instant.parse((response \ "updated").as[String]) shouldBe project.entity.updated
    }

    def validateProjectFolderResponse(response: JsObject): Unit = {
      response.fields should contain allOf(
        "id" -> JsString("folderId"),
        "path" -> JsString("path/to/folder")
      )
    }
  }

  "GET /projects/:id" should {

    "return success response" in new Setup {
      val projectWithId = createProjectWithId()

      projectService.get("projectId") shouldReturn future(Right(projectWithId))
      Get("/projects/projectId").signed.check {
        status shouldBe StatusCodes.OK
        validateExtendedProjectResponse(projectWithId, responseAs[JsObject])
      }
    }

    "return error response when project not found" in new Setup {
      projectService.get("projectId") shouldReturn future(Left(ProjectNotFound))
      Get("/projects/projectId").signed.check {
        status shouldBe StatusCodes.NotFound
        validateErrorResponse(responseAs[JsObject])
      }
    }

    "return error response when user is not owner of project" in new Setup {
      projectService.get("projectId") shouldReturn future(Left(AccessDenied))
      Get("/projects/projectId").signed.check {
        status shouldBe StatusCodes.Forbidden
        validateErrorResponse(responseAs[JsObject])
      }
    }
  }

  "GET /projects" should {

    "return success response" in new Setup {
      val projectWithId = createProjectWithId()

      projectService.listAll shouldReturn future((Seq(projectWithId), 1))
      Get("/projects").signed.check {
        status shouldBe StatusCodes.OK
        val response = responseAs[JsObject]
        response.keys should contain allOf("data", "count")
        (response \ "count").as[Int] shouldBe 1
      }
    }
  }

  "PUT /projects/:id" should {

    "return success response from update project" in new Setup {
      val projectWithId = createProjectWithId()
      val projectCreatedAndUpdatedJson: JsValue = Json.parse("""{"name": "newName"}""")

      projectService.update("projectId", "newName") shouldReturn future(Right(projectWithId))
      Put("/projects/projectId", projectCreatedAndUpdatedJson).signed.check {
        status shouldBe StatusCodes.OK
        validateExtendedProjectResponse(projectWithId, responseAs[JsObject])
      }
    }

    "return error response from update project when project not found" in new Setup {
      val projectCreatedAndUpdatedJson: JsValue = Json.parse("""{"name": "newName"}""")

      projectService.update("projectId", "newName") shouldReturn future(Left(ProjectNotFound))
      Put("/projects/projectId", projectCreatedAndUpdatedJson).signed.check {
        status shouldBe StatusCodes.NotFound
        validateErrorResponse(responseAs[JsObject])
      }
    }

    "return error response from update project when user is not a owner of project" in new Setup {
      val projectCreatedAndUpdatedJson: JsValue = Json.parse("""{"name": "newName"}""")

      projectService.update("projectId", "newName") shouldReturn future(Left(AccessDenied))
      Put("/projects/projectId", projectCreatedAndUpdatedJson).signed.check {
        status shouldBe StatusCodes.Forbidden
        validateErrorResponse(responseAs[JsObject])
      }
    }

    "return error response from update project when project name already exists" in new Setup {
      val projectCreatedAndUpdatedJson: JsValue = Json.parse("""{"name": "newName"}""")

      projectService.update("projectId", "newName") shouldReturn future(Left(ProjectNameAlreadyExists("newName")))
      Put("/projects/projectId", projectCreatedAndUpdatedJson).signed.check {
        status shouldBe StatusCodes.BadRequest
        validateErrorResponse(responseAs[JsObject])
      }
    }
  }

  "POST /projects" should {

    "create project successfully" in new Setup {
      val projectWithId = createProjectWithId()
      val projectCreatedAndUpdatedJson: JsValue = Json.parse("""{"name": "newName"}""")

      projectService.create("newName") shouldReturn future(Right(projectWithId))
      Post("/projects", projectCreatedAndUpdatedJson).signed.check {
        status shouldBe StatusCodes.OK
        validateCreateProjectResponse(projectWithId, responseAs[JsObject])
      }
    }

    "not create the project when project with same name already exists" in new Setup {
      val projectCreatedAndUpdatedJson: JsValue = Json.parse("""{"name": "newName"}""")

      projectService.create("newName") shouldReturn
        future(Left(ProjectServiceCreateError.ProjectNameAlreadyExists("newName")))
      Post("/projects", projectCreatedAndUpdatedJson).signed.check {
        status shouldBe StatusCodes.BadRequest
        validateErrorResponse(responseAs[JsObject])
      }
    }
  }

  "DELETE /projects/:id" should {

    "return success response from delete project" in new Setup {
      projectService.delete("projectId") shouldReturn future(().asRight)
      Delete("/projects/projectId").signed.check {
        status shouldEqual StatusCodes.OK
        responseAs[JsObject] shouldBe JsObject(Seq("id" -> JsString("projectId")))
      }
    }

    "return error response from delete project when project does not exist" in new Setup {
      projectService.delete("projectId") shouldReturn future(Left(ProjectNotFound))
      Delete("/projects/projectId").signed.check {
        status shouldBe StatusCodes.NotFound
        validateErrorResponse(responseAs[JsObject])
      }
    }
  }

  "POST /projects/:projectId/folders" should {
    val projectFolderCreateJsonString: String = """{"path": "path/to/folder"}"""
    val projectFolderCreateJson: JsValue = Json.parse(projectFolderCreateJsonString)

    "create project successfully" in new Setup {
      val folderWithId = WithId(Folder("path/to/folder"), "folderId")
      projectService.createFolder("projectId", "path/to/folder") shouldReturn
        future(folderWithId.asRight)
      Post("/projects/projectId/folders", projectFolderCreateJson).signed.check {
        status shouldEqual StatusCodes.OK
        validateProjectFolderResponse(responseAs[JsObject])
      }
    }

    "not create project when path is not unique" in new Setup {
      projectService.createFolder("projectId", "path/to/folder") shouldReturn
        future(FolderPathIsDuplicate.asLeft)
      Post("/projects/projectId/folders", projectFolderCreateJson).signed.check {
        status shouldEqual StatusCodes.BadRequest
        validateErrorResponse(responseAs[JsObject])
      }
    }
  }

  "GET /projects/:projectId/folders/:folderId" should {

    "return success response from get folder" in new Setup {
      projectService.getFolder("projectId", "folderId") shouldReturn
        future(WithId(Folder("path/to/folder"), "folderId").asRight)
      Get("/projects/projectId/folders/folderId").signed.check {
        status shouldEqual StatusCodes.OK
        validateProjectFolderResponse(responseAs[JsObject])
      }
    }

    "return error response from get folder when folder does not exist" in new Setup {
      projectService.getFolder("projectId", "folderId") shouldReturn
        future(FolderNotFound.asLeft)
      Get("/projects/projectId/folders/folderId").signed.check {
        status shouldBe StatusCodes.NotFound
        validateErrorResponse(responseAs[JsObject])
      }
    }

    "DELETE /projects/:projectId/folders/:folderId" should {

      "return success response from delete folder" in new Setup {
        projectService.deleteFolder("projectId", "folderId") shouldReturn
          future(().asRight)
        Delete("/projects/projectId/folders/folderId").signed.check {
          status shouldEqual StatusCodes.OK
          responseAs[JsObject] shouldBe JsObject(Seq("id" -> JsString("folderId")))
        }
      }

      "return error response from delete folder" in new Setup {
        projectService.deleteFolder("projectId", "folderId") shouldReturn
          future(FolderNotFound.asLeft)
        Delete("/projects/projectId/folders/folderId").signed.check {
          status shouldBe StatusCodes.NotFound
          validateErrorResponse(responseAs[JsObject])
        }
      }

    }
  }

  "PUT /projects/:id/:assetType/:projectAssetId" should {

    "return success response from add asset of albums" in new Setup {

      projectService.addAsset("projectId", Some("folderId"), "assetId", albumService) shouldReturn
        future(().asRight)
      Put("/projects/projectId/albums/assetId", Json.parse("{\"folderId\": \"folderId\"}")).signed.check {
        status shouldEqual StatusCodes.OK
        responseAs[JsObject] shouldBe JsObject(Seq("id" -> JsString("projectId")))
      }
    }

    "return success response from add asset of cv-models" in new Setup {
      projectService.addAsset("projectId", Some("folderId"), "assetId", cvModelService) shouldReturn
        future(().asRight)
      Put("/projects/projectId/cv-models/assetId", Json.parse("{\"folderId\": \"folderId\"}")).signed.check {
        status shouldEqual StatusCodes.OK
        responseAs[JsObject] shouldBe JsObject(Seq("id" -> JsString("projectId")))
      }
    }

    "return success response from add asset of tables" in new Setup {
      projectService.addAsset("projectId", Some("folderId"), "assetId", tableService) shouldReturn
        future(().asRight)
      Put("/projects/projectId/tables/assetId", Json.parse("{\"folderId\": \"folderId\"}")).signed.check {
        status shouldEqual StatusCodes.OK
        responseAs[JsObject] shouldBe JsObject(Seq("id" -> JsString("projectId")))
      }
    }

    "return success response from add asset of cv-predictions" in new Setup {
      projectService.addAsset("projectId", Some("folderId"), "assetId", cvPredictionService) shouldReturn
        future(().asRight)
      Put("/projects/projectId/cv-predictions/assetId", Json.parse("{\"folderId\": \"folderId\"}")).signed.check {
        status shouldEqual StatusCodes.OK
        responseAs[JsObject] shouldBe JsObject(Seq("id" -> JsString("projectId")))
      }
    }

    "return success response from add asset of dc-projects" in new Setup {
      projectService.addAsset("projectId", Some("folderId"), "assetId", dcProjectService) shouldReturn
        future(().asRight)
      Put("/projects/projectId/dc-projects/assetId", Json.parse("{\"folderId\": \"folderId\"}")).signed.check {
        status shouldEqual StatusCodes.OK
        responseAs[JsObject] shouldBe JsObject(Seq("id" -> JsString("projectId")))
      }
    }

    "return success response from add asset of online-jobs" in new Setup {
      projectService.addAsset("projectId", Some("folderId"), "assetId", onlineJobService) shouldReturn
        future(().asRight)
      Put("/projects/projectId/online-jobs/assetId", Json.parse("{\"folderId\": \"folderId\"}")).signed.check {
        status shouldEqual StatusCodes.OK
        responseAs[JsObject] shouldBe JsObject(Seq("id" -> JsString("projectId")))
      }
    }

    "return success response from add asset of experiments" in new Setup {
      projectService.addAsset("projectId", Some("folderId"), "assetId", experimentService) shouldReturn
        future(().asRight)
      Put("/projects/projectId/experiments/assetId", Json.parse("{\"folderId\": \"folderId\"}")).signed.check {
        status shouldEqual StatusCodes.OK
        responseAs[JsObject] shouldBe JsObject(Seq("id" -> JsString("projectId")))
      }
    }

    "return success response from add asset of pipelines" in new Setup {
      projectService.addAsset("projectId", Some("folderId"), "assetId", pipelineService) shouldReturn
        future(().asRight)
      Put("/projects/projectId/pipelines/assetId", Json.parse("{\"folderId\": \"folderId\"}")).signed.check {
        status shouldEqual StatusCodes.OK
        responseAs[JsObject] shouldBe JsObject(Seq("id" -> JsString("projectId")))
      }
    }

    "return success response from add asset of datasets" in new Setup {
      projectService.addAsset("projectId", Some("folderId"), "assetId", datasetService) shouldReturn
        future(().asRight)
      Put("/projects/projectId/datasets/assetId", Json.parse("{\"folderId\": \"folderId\"}")).signed.check {
        status shouldEqual StatusCodes.OK
        responseAs[JsObject] shouldBe JsObject(Seq("id" -> JsString("projectId")))
      }
    }

    "return success response from add asset of tabular models" in new Setup {
      projectService.addAsset("projectId", Some("folderId"), "assetId", tabularModelService) shouldReturn
        future(().asRight)
      Put("/projects/projectId/models/assetId", Json.parse("{\"folderId\": \"folderId\"}")).signed.check {
        status shouldEqual StatusCodes.OK
        responseAs[JsObject] shouldBe JsObject(Seq("id" -> JsString("projectId")))
      }
    }

    "return success response from add asset of tabular predictions" in new Setup {
      projectService.addAsset("projectId", Some("folderId"), "assetId", tabularPredictionService) shouldReturn
        future(().asRight)
      Put("/projects/projectId/predictions/assetId", Json.parse("{\"folderId\": \"folderId\"}")).signed.check {
        status shouldEqual StatusCodes.OK
        responseAs[JsObject] shouldBe JsObject(Seq("id" -> JsString("projectId")))
      }
    }
  }


  "DELETE /projects/:id/:assetType/:projectAssetId" should {

    "successfully delete asset of albums" in new Setup {
      projectService.deleteAsset("projectId", "assetId", albumService) shouldReturn
        future(().asRight)
      Delete("/projects/projectId/albums/assetId").signed.check {
        status shouldEqual StatusCodes.OK
        responseAs[JsObject] shouldBe JsObject(Seq("id" -> JsString("projectId")))
      }
    }

    "successfully delete asset of cv-models" in new Setup {
      projectService.deleteAsset("projectId", "assetId", cvModelService) shouldReturn
        future(().asRight)
      Delete("/projects/projectId/cv-models/assetId").signed.check {
        status shouldEqual StatusCodes.OK
        responseAs[JsObject] shouldBe JsObject(Seq("id" -> JsString("projectId")))
      }
    }

    "successfully delete asset of tables" in new Setup {
      projectService.deleteAsset("projectId", "assetId", tableService) shouldReturn
        future(().asRight)
      Delete("/projects/projectId/tables/assetId").signed.check {
        status shouldEqual StatusCodes.OK
        responseAs[JsObject] shouldBe JsObject(Seq("id" -> JsString("projectId")))
      }
    }

    "successfully delete asset of cv-predictions" in new Setup {
      projectService.deleteAsset("projectId", "assetId", cvPredictionService) shouldReturn
        future(().asRight)
      Delete("/projects/projectId/cv-predictions/assetId").signed.check {
        status shouldEqual StatusCodes.OK
        responseAs[JsObject] shouldBe JsObject(Seq("id" -> JsString("projectId")))
      }
    }

    "successfully delete asset of dc-projects" in new Setup {
      projectService.deleteAsset("projectId", "assetId", dcProjectService) shouldReturn
        future(().asRight)
      Delete("/projects/projectId/dc-projects/assetId").signed.check {
        status shouldEqual StatusCodes.OK
        responseAs[JsObject] shouldBe JsObject(Seq("id" -> JsString("projectId")))
      }
    }

    "successfully delete asset of online-jobs" in new Setup {
      projectService.deleteAsset("projectId", "assetId", onlineJobService) shouldReturn
        future(().asRight)
      Delete("/projects/projectId/online-jobs/assetId").signed.check {
        status shouldEqual StatusCodes.OK
        responseAs[JsObject] shouldBe JsObject(Seq("id" -> JsString("projectId")))
      }
    }

    "successfully delete asset of experiments" in new Setup {
      projectService.deleteAsset("projectId", "assetId", experimentService) shouldReturn
        future(().asRight)
      Delete("/projects/projectId/experiments/assetId").signed.check {
        status shouldEqual StatusCodes.OK
        responseAs[JsObject] shouldBe JsObject(Seq("id" -> JsString("projectId")))
      }
    }

    "successfully delete asset of pipelines" in new Setup {
      projectService.deleteAsset("projectId", "assetId", pipelineService) shouldReturn
        future(().asRight)
      Delete("/projects/projectId/pipelines/assetId").signed.check {
        status shouldEqual StatusCodes.OK
        responseAs[JsObject] shouldBe JsObject(Seq("id" -> JsString("projectId")))
      }
    }

    "successfully delete asset of datasets" in new Setup {
      projectService.deleteAsset("projectId", "assetId", datasetService) shouldReturn
        future(().asRight)
      Delete("/projects/projectId/datasets/assetId").signed.check {
        status shouldEqual StatusCodes.OK
        responseAs[JsObject] shouldBe JsObject(Seq("id" -> JsString("projectId")))
      }
    }

    "successfully delete asset of tabular models" in new Setup {
      projectService.deleteAsset("projectId", "assetId", tabularModelService) shouldReturn
        future(().asRight)
      Delete("/projects/projectId/models/assetId").signed.check {
        status shouldEqual StatusCodes.OK
        responseAs[JsObject] shouldBe JsObject(Seq("id" -> JsString("projectId")))
      }
    }

    "successfully delete asset of tabular predictions" in new Setup {
      projectService.deleteAsset("projectId", "assetId", tabularPredictionService) shouldReturn
        future(().asRight)
      Delete("/projects/projectId/predictions/assetId").signed.check {
        status shouldEqual StatusCodes.OK
        responseAs[JsObject] shouldBe JsObject(Seq("id" -> JsString("projectId")))
      }
    }
  }

}
