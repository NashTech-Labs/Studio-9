package baile.services.project

import java.util.UUID

import baile.BaseSpec
import baile.dao.project.ProjectDao
import baile.dao.project.ProjectDao.NameIs
import baile.daocommons.WithId
import baile.daocommons.filters.Filter
import baile.daocommons.sorting.SortBy
import baile.domain.asset.{ AssetReference, AssetType }
import baile.domain.project.{ ProjectAssetReference, Folder, Project }
import baile.domain.usermanagement.User
import baile.services.cv.model.CVModelService
import baile.services.cv.model.CVModelService.CVModelServiceError
import baile.services.cv.prediction.CVPredictionService
import baile.services.cv.prediction.CVPredictionService.CVPredictionServiceError
import baile.services.images.AlbumService
import baile.services.images.AlbumService.AlbumServiceError
import baile.services.project.ProjectService.ProjectServiceCreateError
import baile.services.project.ProjectService.ProjectServiceError._
import baile.services.project.util.TestData._
import baile.services.usermanagement.util.TestData.SampleUser
import cats.implicits._
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito.when

import scala.concurrent.ExecutionContext

class ProjectServiceSpec extends BaseSpec {

  val dao: ProjectDao = mock[ProjectDao]
  val albumService: AlbumService = mock[AlbumService]
  val cvModelService: CVModelService = mock[CVModelService]
  val cvPredictionService: CVPredictionService = mock[CVPredictionService]
  val service: ProjectService = new ProjectService(dao)
  implicit val user: User = SampleUser

  when(albumService.assetType).thenReturn(AssetType.Album)
  when(cvModelService.assetType).thenReturn(AssetType.CvModel)
  when(cvPredictionService.assetType).thenReturn(AssetType.CvPrediction)

  when(albumService.forbiddenError).thenReturn(AlbumServiceError.AccessDenied)
  when(cvModelService.forbiddenError).thenReturn(CVModelServiceError.AccessDenied)
  when(cvPredictionService.forbiddenError).thenReturn(CVPredictionServiceError.AccessDenied)

  when(albumService.sortingFieldNotFoundError).thenReturn(AlbumServiceError.SortingFieldUnknown)
  when(cvModelService.sortingFieldNotFoundError).thenReturn(CVModelServiceError.SortingFieldUnknown)
  when(cvPredictionService.sortingFieldNotFoundError).thenReturn(CVPredictionServiceError.SortingFieldUnknown)

  "ProjectService#create" should {

    "create project successfully" in {

      when(dao.create(any[Project])(any[ExecutionContext])) thenReturn future("projectId")
      when(dao.count(filterContains(NameIs("name")))(any[ExecutionContext])) thenReturn future(0)
      whenReady(service.create("name")) { response =>
        val project = response.right.get
        WithId(project.entity.copy(
          created = Now,
          updated = Now
        ), project.id) shouldBe WithId(Project("name", Now, Now, SampleUser.id, Seq.empty, Seq.empty), "projectId")
      }
    }

    "not create project when project already exist with same name" in {
      when(dao.count(filterContains(NameIs("name")))(any[ExecutionContext])) thenReturn future(1)
      whenReady(service.create("name")) { response =>
        response shouldBe Left(ProjectServiceCreateError.ProjectNameAlreadyExists("name"))
      }
    }
  }

  "ProjectService#createFolder" should {

    "create folder successfully when no parent folder exists" in {
      val folderWithId = WithId(Folder("path/to/folder"), "folderId")
      when(dao.addFolder(anyString(), any[Folder])(any[ExecutionContext])).thenReturn(
        future(Some(folderWithId))
      )
      when(dao.get(
        any[String]
      )(any[ExecutionContext])) thenReturn future(Some(ProjectWithIdSample))
      whenReady(service.createFolder("project-id", "path-to-folder")){ response =>
        response shouldBe folderWithId.asRight
      }
    }

    "not create folder when folder path is duplicate" in {
      when(dao.get(
        any[String]
      )(any[ExecutionContext])) .thenReturn(
        future(Some(WithId(ProjectSample.copy(folders = Seq(WithId(Folder("path-to-folder"), "fId"))), "project-id")))
      )
      whenReady(service.createFolder("project-id", "path-to-folder")){ response =>
        response shouldBe FolderPathIsDuplicate.asLeft
      }
    }

  }

  "ProjectService#getFolder" should {

    "get folder successfully" in {
      val sampleProjectWithId = WithId(
        ProjectSample.copy(folders = Seq(WithId(Folder("/path/to/folder"), "folder-id"))),
        "ProjectId"
      )
      when(dao.removeFolder(anyString(), any[String])(any[ExecutionContext])).thenReturn(
        future(Some(ProjectWithIdSample))
      )
      when(dao.get(
        any[String]
      )(any[ExecutionContext])) thenReturn future(Some(sampleProjectWithId))
      whenReady(service.getFolder("project-id", "folder-id")){ response =>
        response shouldBe WithId(FolderSample, "folder-id").asRight
      }
    }

  }

  "ProjectService#deleteFolders" should {

    "get folder successfully" in {
      val sampleProjectWithId = WithId(
        ProjectSample.copy(folders = Seq(WithId(Folder("path"), "folder-id"))),
        "ProjectId"
      )
      when(dao.get(
        any[String]
      )(any[ExecutionContext])) thenReturn future(Some(sampleProjectWithId))
      whenReady(service.deleteFolder("project-id", "folder-id")){ response =>
        response shouldBe ().asRight
      }
    }

  }

  "ProjectService#list" should {

    "return list of projects" in {
      when(dao.listAll(
        any[Filter],
        any[Option[SortBy]]
      )(any[ExecutionContext])) thenReturn future(Seq(WithId(ProjectSample, "ProjectId")))
      when(dao.count(any[Filter])(any[ExecutionContext])) thenReturn future(1)
      whenReady(service.listAll) { response =>
        val (list, count) = response
        count shouldBe 1
        list shouldBe Seq(WithId(ProjectSample, "ProjectId"))
      }
    }
  }

  "ProjectService#update" should {

    "update project successfully" in {
      when(dao.count(
        any[Filter]
      )(any[ExecutionContext])) thenReturn future(0)
      when(dao.get(
        any[String]
      )(any[ExecutionContext])) thenReturn future(Some(ProjectWithIdSample))
      when(dao.update(
        any[String],
        any[Project => Project].apply
      )(any[ExecutionContext])) thenReturn future(Some(ProjectWithIdSample))
      whenReady(service.update("ProjectId", "name")) { response =>
        response shouldBe Right(ProjectWithIdSample)
      }
    }

    "be unable to update non-existing project" in {
      when(dao.count(
        any[Filter]
      )(any[ExecutionContext])) thenReturn future(0)
      when(dao.get(
        any[String]
      )(any[ExecutionContext])) thenReturn future(None)
      whenReady(service.update("ProjectId", "name")) { response =>
        response shouldBe Left(ProjectNotFound)
      }
    }

    "be unable to update the project when user already has same name of project" in {
      when(dao.count(
        any[Filter]
      )(any[ExecutionContext])) thenReturn future(1)

      whenReady(service.update("id", "name")) { response =>
        response shouldBe Left(ProjectNameAlreadyExists("name"))
      }
    }
  }

  "ProjectService#delete" should {

    "delete project successfully" in {
      when(dao.get(
        any[String]
      )(any[ExecutionContext])) thenReturn future(Some(ProjectWithIdSample))
      when(dao.delete(
        any[String]
      )(any[ExecutionContext])) thenReturn future(true)
      whenReady(service.delete("id")) {
        _ shouldBe Right(())
      }
    }

    "unable to delete non-existing project successfully" in {
      when(dao.get(
        any[String]
      )(any[ExecutionContext])) thenReturn future(None)
      whenReady(service.delete("id")) {
        _ shouldBe Left(ProjectNotFound)
      }
    }

    "unable to delete when user is not owner of  project" in {
      when(dao.get(
        any[String]
      )(any[ExecutionContext])) thenReturn future(Some(ProjectWithIdSample))
      whenReady(service.delete("id")(user = SampleUser.copy(id = UUID.randomUUID))) {
        _ shouldBe Left(AccessDenied)
      }
    }
  }

  "ProjectService#addAsset" should {

    "be able to add album in project" in {
      when(albumService.get(
        any[String]
      )(any[User])) thenReturn future(AlbumEntityWithId.asRight)
      when(dao.get(
        any[String]
      )(any[ExecutionContext])) thenReturn future(Some(ProjectWithIdSample))
      when(dao.addAsset(
        any[String],
        any[ProjectAssetReference]
      )(any[ExecutionContext])) thenReturn future(Some(ProjectWithIdSample))

      whenReady(service.addAsset("projectId", None, "newAssetId", albumService)) {
        _ shouldBe Right(())
      }
    }

    "not be able to add album in project when user is not owner of album " in {
      when(albumService.get(
        any[String]
      )(any[User])) thenReturn future(AlbumServiceError.AccessDenied.asLeft)

      whenReady(service.addAsset("projectId", None, "newAssetId", albumService)) {
        _ shouldBe Left(AccessDenied)
      }
    }

    "be able to add cvModel in project" in {
      when(cvModelService.get(
        any[String]
      )(any[User])) thenReturn future(
        CVModelEntityWithId.asRight)
      when(dao.get(
        any[String]
      )(any[ExecutionContext])) thenReturn future(Some(ProjectWithIdSample))
      when(dao.addAsset(
        any[String],
        any[ProjectAssetReference]
      )(any[ExecutionContext])) thenReturn future(Some(ProjectWithIdSample))

      whenReady(service.addAsset("projectId", None, "newAssetId", cvModelService)) {
        _ shouldBe Right(())
      }
    }

    "not be able to add cvModel in project when user is not owner of cvModel " in {
      when(cvModelService.get(
        any[String]
      )(any[User])) thenReturn future(CVModelServiceError.AccessDenied.asLeft)

      whenReady(service.addAsset("projectId", None, "newAssetId", cvModelService)) {
        _ shouldBe Left(AccessDenied)
      }
    }

    "be able to add cvPrediction in project" in {
      when(cvPredictionService.get(
        any[String]
      )(any[User])) thenReturn future(CVPredictionWithIdEntity.asRight)
      when(dao.get(
        any[String]
      )(any[ExecutionContext])) thenReturn future(Some(ProjectWithIdSample))
      when(dao.addAsset(
        any[String],
        any[ProjectAssetReference]
      )(any[ExecutionContext])) thenReturn future(Some(ProjectWithIdSample))

      whenReady(service.addAsset("projectId", None, "newAssetId", cvPredictionService)) {
        _ shouldBe Right(())
      }
    }

    "not be able to add cvPrediction in project when user is not owner of cvPrediction " in {
      when(cvPredictionService.get(
        any[String]
      )(any[User])) thenReturn future(CVPredictionServiceError.AccessDenied.asLeft)

      whenReady(service.addAsset("projectId", None, "newAssetId", cvPredictionService)) {
        _ shouldBe Left(AccessDenied)
      }
    }

    "not be able to add asset when asset already exist in project" in {
      when(albumService.get(
        any[String]
      )(any[User])) thenReturn future(AlbumEntityWithId.asRight)
      val assetFolderReference = Seq(ProjectAssetReference(AssetReference("albumId", AssetType.Album), None))
      val project = ProjectSample.copy(assets = assetFolderReference)
      when(dao.get(
        any[String]
      )(any[ExecutionContext])) thenReturn future(Some(WithId(project, "projectId")))

      whenReady(service.addAsset("projectId", None, "albumId", albumService)) {
        _ shouldBe Left(AssetAlreadyExistsInProject)
      }
    }
  }

  "ProjectService#deleteAsset" should {
    "be able to delete asset in project" in {
      val assetFolderReference = Seq(ProjectAssetReference(AssetReference("cvModelId", AssetType.CvModel), None))
      val project = ProjectSample.copy(assets = assetFolderReference)
      when(dao.get(
        any[String]
      )(any[ExecutionContext])) thenReturn future(Some(WithId(project, "projectId")))

      when(dao.removeAsset(
        any[String],
        any[AssetReference]
      )(any[ExecutionContext])) thenReturn future(Some(WithId(project, "projectId")))

      whenReady(service.deleteAsset("projectId", "cvModelId", cvModelService)) {
        _ shouldBe Right(())
      }
    }

    "not be able to delete asset when asset not-exist in project" in {

      when(dao.get(
        any[String]
      )(any[ExecutionContext])) thenReturn future(Some(ProjectWithIdSample))

      whenReady(service.deleteAsset("projectId", "newAssetId", cvModelService)) {
        _ shouldBe Left(AssetNotFound)
      }
    }
  }

}
