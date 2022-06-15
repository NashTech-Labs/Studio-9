package baile.services.dcproject

import java.time.Instant
import java.util.UUID

import akka.stream.scaladsl.Source
import akka.util.ByteString
import baile.{ ExtendedBaseSpec, RandomGenerators }
import baile.dao.dcproject.{ DCProjectDao, DCProjectPackageDao }
import baile.daocommons.WithId
import baile.domain.asset.{ AssetReference, AssetType }
import baile.domain.dcproject.DCProjectStatus.Interactive
import baile.domain.dcproject.{ DCProject, DCProjectStatus }
import baile.services.asset.sharing.AssetSharingService
import baile.services.dcproject.DCProjectService.DCProjectServiceError
import baile.services.dcproject.DCProjectService.DCProjectServiceError.{ AccessDenied, DCProjectNotFound }
import baile.services.process.ProcessService
import baile.services.project.ProjectService
import baile.services.remotestorage.{ Directory, File, RemoteStorageService, StreamedFile }
import baile.services.usermanagement.util.TestData.SampleUser
import cats.implicits._

class DCProjectServiceSpec extends ExtendedBaseSpec {

  trait Setup {
    val dao = mock[DCProjectDao]
    val sessionService = mock[SessionService]
    val projectService = mock[ProjectService]
    val processService = mock[ProcessService]
    val assetSharingService = mock[AssetSharingService]
    val packageDao = mock[DCProjectPackageDao]
    val fileStorage = mock[RemoteStorageService]
    val service = new DCProjectService(
      dao,
      sessionService,
      projectService,
      fileStorage,
      conf.getConfig("dc-project"),
      processService,
      assetSharingService,
      packageDao
    )

    implicit val user = SampleUser
    val description = "My new project"
    val project = WithId(
      DCProject(
        ownerId = user.id,
        name = "project1",
        status = DCProjectStatus.Idle,
        created = Instant.now,
        updated = Instant.now,
        description = Some(description),
        basePath = "projects/basePath",
        packageName = None,
        latestPackageVersion = None
      ),
      RandomGenerators.randomString()
    )
  }

  "DCProjectService#create" should {

    "create new dc project" in new Setup {

      dao.count(*) shouldReturn future(0)
      dao.create(*[String => DCProject]) shouldReturn future(project)

      whenReady(service.create(Some(project.entity.name), Some(description)))(_ shouldBe project.asRight)
    }

    "return error when name is already taken" in new Setup {
      dao.count(*) shouldReturn future(1)
      whenReady(service.create(Some("project"), None))(_ shouldBe DCProjectServiceError.NameIsTaken.asLeft)
    }

  }

  "DCProjectService#update" should {

    "update project name and description" in new Setup {
      val newName = "newName"
      val updatedProject = project.copy(entity = project.entity.copy(name = newName))

      dao.get(project.id) shouldReturn future(Some(project))
      dao.count(*) shouldReturn future(0)
      dao.update(project.id, *) shouldReturn future(Some(updatedProject))

      whenReady(service.update(project.id, Some(newName), None))(_ shouldBe updatedProject.asRight)
    }

    "return error when name is already taken" in new Setup {
      dao.get(project.id) shouldReturn future(Some(project))
      dao.count(*) shouldReturn future(1)
      whenReady(service.update(project.id, Some("newName"), None))(_ shouldBe DCProjectServiceError.NameIsTaken.asLeft)
    }

  }

  "DCProjectService#updateFile" should {

    val path = "path/to/file1"
    val file = File(path, 256L, Instant.now)
    val content = Source[ByteString](List(ByteString(RandomGenerators.randomString(100))))

    "return error if Project is not in Idle mode" in new Setup {
      val projectIsNotInIdleMode = project.copy(entity = project.entity.copy(status = Interactive))

      dao.get(project.id) shouldReturn future(Some(projectIsNotInIdleMode))

      whenReady(service.updateFile(project.id, path, Instant.now.minusSeconds(30), content))(
        _ shouldBe DCProjectServiceError.ProjectIsNotInIdleMode.asLeft
      )
    }

    "rewrite content of a file" in new Setup {
      dao.get(project.id) shouldReturn future(Some(project))
      path willBe returned by fileStorage.path(*, eqTo(project.entity.basePath, path))
      path willBe returned by fileStorage.path(*, eqTo(project.entity.basePath, ""))
      fileStorage.doesExist(path) shouldReturn future(true)
      fileStorage.doesDirectoryExist(path) shouldReturn future(false)
      fileStorage.readMeta(path) shouldReturn future(file)
      fileStorage.write(content, path) shouldReturn future(file)

      whenReady(service.updateFile(project.id, path, file.lastModified, content))(_ shouldBe file.asRight)
    }

    "return error when file was updated since provided instant" in new Setup {
      dao.get(project.id) shouldReturn future(Some(project))
      path willBe returned by fileStorage.path(*, eqTo(project.entity.basePath, path))
      fileStorage.doesExist(path) shouldReturn future(true)
      fileStorage.doesDirectoryExist(path) shouldReturn future(false)
      fileStorage.readMeta(path) shouldReturn future(file)

      whenReady(service.updateFile(project.id, path, file.lastModified.minusSeconds(30), content))(
        _ shouldBe DCProjectServiceError.FileWasUpdated.asLeft
      )
    }

  }

  "DCProjectService#createFile" should {

    val path = "path/to/file1"
    val subPath1 = "path"
    val subPath2 = "path/to"
    val file = File(path, 256L, Instant.now)
    val content = Source[ByteString](List(ByteString(RandomGenerators.randomString(100))))

    "return error if Project is not in Idle mode" in new Setup {
      val projectIsNotInIdleMode = project.copy(entity = project.entity.copy(status = Interactive))

      dao.get(project.id) shouldReturn future(Some(projectIsNotInIdleMode))

      whenReady(service.createFile(project.id, path, content))(
        _ shouldBe DCProjectServiceError.ProjectIsNotInIdleMode.asLeft
      )
    }

    "create new file" in new Setup {
      dao.get(project.id) shouldReturn future(Some(project))
      path willBe returned by fileStorage.path(*, eqTo(project.entity.basePath, path))
      path willBe returned by fileStorage.path(*, eqTo(project.entity.basePath, ""))
      subPath1 willBe returned by fileStorage.path(*, eqTo(project.entity.basePath, subPath1))
      subPath2 willBe returned by fileStorage.path(*, eqTo(project.entity.basePath, subPath2))
      future(false) willBe returned by fileStorage.doesExist(subPath1)
      future(true) willBe returned by fileStorage.doesDirectoryExist(subPath1)
      future(false) willBe returned by fileStorage.doesExist(subPath2)
      future(true) willBe returned by fileStorage.doesDirectoryExist(subPath2)
      future(false) willBe returned by fileStorage.doesExist(path)
      future(false) willBe returned by fileStorage.doesDirectoryExist(path)
      fileStorage.write(content, path) shouldReturn future(file)

      whenReady(service.createFile(project.id, path, content))(
        _ shouldBe file.asRight
      )
    }

    "return error when directory exists in the target path" in new Setup {
      dao.get(project.id) shouldReturn future(Some(project))
      fileStorage.path(*, *) shouldReturn path
      fileStorage.doesExist(*) shouldReturn future(false)
      fileStorage.doesDirectoryExist(*) shouldReturn future(true)

      whenReady(service.createFile(project.id, path, content))(
        _ shouldBe DCProjectServiceError.ObjectAlreadyExists.asLeft
      )
    }

    "return error when path contains non existing folders" in new Setup {
      dao.get(project.id) shouldReturn future(Some(project))
      subPath1 willBe returned by fileStorage.path(*, eqTo(project.entity.basePath, subPath1))
      fileStorage.doesExist(subPath1) shouldReturn future(false)
      fileStorage.doesDirectoryExist(subPath1) shouldReturn future(false)

      whenReady(service.createFile(project.id, path, content))(
        _ shouldBe DCProjectServiceError.PathNotFound(subPath1).asLeft
      )
    }

  }

  "DCProjectService#copyFile" should {

    val oldPath = "path/to/file/old"
    val newPath = "path/to/file/new"
    val newPath1 = "path"
    val newPath2 = "path/to"
    val newPath3 = "path/to/file"
    val newFile = File(newPath, 256L, Instant.now)

    "copy file from one path to another" in new Setup {
      dao.get(project.id) shouldReturn future(Some(project))
      oldPath willBe returned by fileStorage.path(*, eqTo(project.entity.basePath, oldPath))
      newPath1 willBe returned by fileStorage.path(*, eqTo(project.entity.basePath, newPath1))
      newPath2 willBe returned by fileStorage.path(*, eqTo(project.entity.basePath, newPath2))
      newPath3 willBe returned by fileStorage.path(*, eqTo(project.entity.basePath, newPath3))
      future(true) willBe returned by fileStorage.doesExist(oldPath)
      future(false) willBe returned by fileStorage.doesExist(newPath)
      future(false) willBe returned by fileStorage.doesExist(newPath1)
      future(false) willBe returned by fileStorage.doesExist(newPath2)
      future(false) willBe returned by fileStorage.doesExist(newPath3)
      future(false) willBe returned by fileStorage.doesDirectoryExist(newPath)
      future(true) willBe returned by fileStorage.doesDirectoryExist(newPath1)
      future(true) willBe returned by fileStorage.doesDirectoryExist(newPath2)
      future(true) willBe returned by fileStorage.doesDirectoryExist(newPath3)
      newPath willBe returned by fileStorage.path(*, eqTo(project.entity.basePath, newPath))
      future(newFile) willBe returned by fileStorage.copy(oldPath, newPath)
      newPath willBe returned by fileStorage.path(*, eqTo(project.entity.basePath, ""))

      whenReady(service.copyFile(project.id, oldPath, newPath))(_ shouldBe newFile.asRight)
    }

    "return error if Project is not in Idle mode" in new Setup {
      val projectIsNotInIdleMode = project.copy(entity = project.entity.copy(status = Interactive))
      dao.get(project.id) shouldReturn future(Some(projectIsNotInIdleMode))

      whenReady(service.copyFile(project.id, oldPath, newPath)) {
        _ shouldBe DCProjectServiceError.ProjectIsNotInIdleMode.asLeft
      }
    }

    "return error when file already exists in the target (new) path" in new Setup {
      dao.get(project.id) shouldReturn future(Some(project))
      oldPath willBe returned by fileStorage.path(*, eqTo(project.entity.basePath, oldPath))
      newPath1 willBe returned by fileStorage.path(*, eqTo(project.entity.basePath, newPath1))
      newPath2 willBe returned by fileStorage.path(*, eqTo(project.entity.basePath, newPath2))
      newPath3 willBe returned by fileStorage.path(*, eqTo(project.entity.basePath, newPath3))
      future(true) willBe returned by fileStorage.doesExist(oldPath)
      future(true) willBe returned by fileStorage.doesExist(newPath)
      future(false) willBe returned by fileStorage.doesExist(newPath1)
      future(false) willBe returned by fileStorage.doesExist(newPath2)
      future(false) willBe returned by fileStorage.doesExist(newPath3)
      future(false) willBe returned by fileStorage.doesDirectoryExist(newPath)
      future(true) willBe returned by fileStorage.doesDirectoryExist(newPath1)
      future(true) willBe returned by fileStorage.doesDirectoryExist(newPath2)
      future(true) willBe returned by fileStorage.doesDirectoryExist(newPath3)
      newPath willBe returned by fileStorage.path(*, eqTo(project.entity.basePath, newPath))

      whenReady(service.copyFile(project.id, oldPath, newPath)) {
        _ shouldBe DCProjectServiceError.ObjectAlreadyExists.asLeft
      }
    }

  }

  "DCProjectService#moveFile" should {

    val oldPath = "path/to/file/old"
    val newPath = "path/to/file/new"
    val newPath1 = "path"
    val newPath2 = "path/to"
    val newPath3 = "path/to/file"
    val newFile = File(newPath, 256L, Instant.now)

    "move file from one path to another" in new Setup {
      dao.get(project.id) shouldReturn future(Some(project))
      oldPath willBe returned by fileStorage.path(*, eqTo(project.entity.basePath, oldPath))
      newPath1 willBe returned by fileStorage.path(*, eqTo(project.entity.basePath, newPath1))
      newPath2 willBe returned by fileStorage.path(*, eqTo(project.entity.basePath, newPath2))
      newPath3 willBe returned by fileStorage.path(*, eqTo(project.entity.basePath, newPath3))
      future(true) willBe returned by fileStorage.doesExist(oldPath)
      future(false) willBe returned by fileStorage.doesExist(newPath)
      future(false) willBe returned by fileStorage.doesExist(newPath1)
      future(false) willBe returned by fileStorage.doesExist(newPath2)
      future(false) willBe returned by fileStorage.doesExist(newPath3)
      future(false) willBe returned by fileStorage.doesDirectoryExist(newPath)
      future(true) willBe returned by fileStorage.doesDirectoryExist(newPath1)
      future(true) willBe returned by fileStorage.doesDirectoryExist(newPath2)
      future(true) willBe returned by fileStorage.doesDirectoryExist(newPath3)
      newPath willBe returned by fileStorage.path(*, eqTo(project.entity.basePath, newPath))
      future(newFile) willBe returned by fileStorage.move(oldPath, newPath)
      newPath willBe returned by fileStorage.path(*, eqTo(project.entity.basePath, ""))

      whenReady(service.moveFile(project.id, oldPath, newPath))(_ shouldBe newFile.asRight)
    }

    "return error if Project is not in Idle mode" in new Setup {
      val projectIsNotInIdleMode = project.copy(entity = project.entity.copy(status = Interactive))
      dao.get(project.id) shouldReturn future(Some(projectIsNotInIdleMode))

      whenReady(service.moveFile(project.id, oldPath, newPath)) {
        _ shouldBe DCProjectServiceError.ProjectIsNotInIdleMode.asLeft
      }
    }

    "return error when file already exists in the target (new) path" in new Setup {
      dao.get(project.id) shouldReturn future(Some(project))
      oldPath willBe returned by fileStorage.path(*, eqTo(project.entity.basePath, oldPath))
      newPath1 willBe returned by fileStorage.path(*, eqTo(project.entity.basePath, newPath1))
      newPath2 willBe returned by fileStorage.path(*, eqTo(project.entity.basePath, newPath2))
      newPath3 willBe returned by fileStorage.path(*, eqTo(project.entity.basePath, newPath3))
      future(true) willBe returned by fileStorage.doesExist(oldPath)
      future(true) willBe returned by fileStorage.doesExist(newPath)
      future(false) willBe returned by fileStorage.doesExist(newPath1)
      future(false) willBe returned by fileStorage.doesExist(newPath2)
      future(false) willBe returned by fileStorage.doesExist(newPath3)
      future(false) willBe returned by fileStorage.doesDirectoryExist(newPath)
      future(true) willBe returned by fileStorage.doesDirectoryExist(newPath1)
      future(true) willBe returned by fileStorage.doesDirectoryExist(newPath2)
      future(true) willBe returned by fileStorage.doesDirectoryExist(newPath3)
      newPath willBe returned by fileStorage.path(*, eqTo(project.entity.basePath, newPath))

      whenReady(service.moveFile(project.id, oldPath, newPath)) {
        _ shouldBe DCProjectServiceError.ObjectAlreadyExists.asLeft
      }
    }

  }

  "DCProjectService#getFileContent" should {

    val path = "path/to/file"

    "return source with file content" in new Setup {
      val content = "scala akka http { } { }"
      val contentSource = Source[ByteString](List(ByteString(content)))
      dao.get(project.id) shouldReturn future(Some(project))
      fileStorage.path(*, *) shouldReturn path
      fileStorage.doesExist(*) shouldReturn future(true)
      fileStorage.streamFile(path) shouldReturn future(StreamedFile(File(path, 256L, Instant.now), contentSource))

      whenReady(service.getFileContent(project.id, path)) { eitherResult =>
        val source = eitherResult.right.get
        whenReady(source.runReduce(_ ++ _)) { resultContent =>
          resultContent.utf8String shouldBe content
        }
      }
    }

    "return error when file was not found" in new Setup {
      dao.get(project.id) shouldReturn future(Some(project))
      fileStorage.path(*, *) shouldReturn path
      fileStorage.doesExist(*) shouldReturn future(false)

      whenReady(service.getFileContent(project.id, path))(_ shouldBe DCProjectServiceError.ObjectNotFound.asLeft)
    }

  }

  "DCProjectService#getFile" should {

    val path = "path/to/file"
    val file = File(path, 256L, Instant.now)

    "return file with last modified time" in new Setup {
      dao.get(project.id) shouldReturn future(Some(project))
      fileStorage.path(*, *) shouldReturn path
      fileStorage.doesExist(*) shouldReturn future(true)
      fileStorage.readMeta(*) shouldReturn future(file)

      whenReady(service.getFile(project.id, path)) { eitherResult =>
        val result = eitherResult.right.get
        result shouldBe file
      }
    }

    "return error when file was not found" in new Setup {
      dao.get(project.id) shouldReturn future(Some(project))
      fileStorage.path(*, *) shouldReturn path
      fileStorage.doesExist(*) shouldReturn future(false)

      whenReady(service.getFile(project.id, path))(_ shouldBe DCProjectServiceError.ObjectNotFound.asLeft)
    }

  }

  "DCProjectService#createFolder" should {

    val path = "path/to/folder1"
    val subPath1 = "path"
    val subPath2 = "path/to"
    val directory = Directory(path)

    "create new folder in storage" in new Setup {
      dao.get(project.id) shouldReturn future(Some(project))
      subPath1 willBe returned by fileStorage.path(*, eqTo(project.entity.basePath, subPath1))
      subPath2 willBe returned by fileStorage.path(*, eqTo(project.entity.basePath, subPath2))
      path willBe returned by fileStorage.path(*, eqTo(project.entity.basePath, path))
      path willBe returned by fileStorage.path(*, eqTo(project.entity.basePath, ""))
      fileStorage.doesExist(*) shouldReturn future(false)
      fileStorage.doesDirectoryExist(path) shouldReturn
        future(false)
      fileStorage.doesDirectoryExist(subPath1) shouldReturn future(true)
      fileStorage.doesDirectoryExist(subPath2) shouldReturn future(true)
      fileStorage.createDirectory(*) shouldReturn future(directory)

      whenReady(service.createFolder(project.id, path))(_ shouldBe directory.asRight)
    }

    "return error when Project is not in Idle mode" in new Setup {
      val projectIsNotInIdleMode = project.copy(entity = project.entity.copy(status = Interactive))
      dao.get(project.id) shouldReturn future(Some(projectIsNotInIdleMode))

      whenReady(service.createFolder(project.id, path))(_ shouldBe DCProjectServiceError.ProjectIsNotInIdleMode.asLeft)
    }

    "return error when folder already exists" in new Setup {
      dao.get(project.id) shouldReturn future(Some(project))
      fileStorage.path(*, *) shouldReturn path
      fileStorage.doesExist(*) shouldReturn future(false)
      fileStorage.doesDirectoryExist(*) shouldReturn future(true)

      whenReady(service.createFolder(project.id, path))(_ shouldBe DCProjectServiceError.ObjectAlreadyExists.asLeft)
    }

    "return error when path provided is invalid" in new Setup {
      dao.get(project.id) shouldReturn future(Some(project))
      subPath1 willBe returned by fileStorage.path(*, eqTo(project.entity.basePath, subPath1))
      fileStorage.doesExist(subPath1) shouldReturn future(false)
      fileStorage.doesDirectoryExist(subPath1) shouldReturn
        future(false)

      whenReady(service.createFolder(project.id, path))(
        _ shouldBe DCProjectServiceError.PathNotFound(subPath1).asLeft
      )
    }

  }

  "DCProjectService#listFolder" should {

    "return list of files and directories inside the folder" in new Setup {
      val prefix = "baile/data/project/prefix"
      val storedObjects = List(
        File(RandomGenerators.randomString(), RandomGenerators.randomInt(20000), Instant.now),
        File(RandomGenerators.randomString(), RandomGenerators.randomInt(20000), Instant.now),
        File(RandomGenerators.randomString(), RandomGenerators.randomInt(20000), Instant.now),
        File(RandomGenerators.randomString(), RandomGenerators.randomInt(20000), Instant.now),
        Directory(RandomGenerators.randomString()),
        Directory(RandomGenerators.randomString()),
        Directory(RandomGenerators.randomString()),
        File(RandomGenerators.randomString(), RandomGenerators.randomInt(20000), Instant.now),
        File(RandomGenerators.randomString(), RandomGenerators.randomInt(20000), Instant.now),
        File(RandomGenerators.randomString(), RandomGenerators.randomInt(20000), Instant.now),
        Directory(RandomGenerators.randomString()),
        File(RandomGenerators.randomString(), RandomGenerators.randomInt(20000), Instant.now)
      ).map { storedObject =>
        storedObject.updatePath(prefix + "/" + storedObject.path)
      }

      dao.get(project.id) shouldReturn future(Some(project))
      fileStorage.path(*, *) shouldReturn prefix
      fileStorage.listDirectory(*, recursive = false) shouldReturn future(storedObjects)

      whenReady(service.listFolder(project.id, None, recursive = false)) { result =>
        result shouldBe storedObjects.map { storedObject =>
          storedObject.updatePath(storedObject.path.stripPrefix(prefix + "/"))
        }.asRight
      }
    }

  }

  "DCProjectService#moveFolder" should {

    val oldPath = "path/to/folder/old"
    val newPath = "path/to/folder/new"
    val directory = Directory(newPath)

    "move folder from one path to another" in new Setup {
      dao.get(project.id) shouldReturn future(Some(project))
      fileStorage.path(*, eqTo(project.entity.basePath, oldPath)) shouldReturn oldPath
      fileStorage.listDirectory(oldPath, recursive = false) shouldReturn future(List.empty)
      fileStorage.path(*, eqTo(project.entity.basePath, newPath)) shouldReturn newPath
      fileStorage.path(*, eqTo(project.entity.basePath, "")) shouldReturn newPath
      fileStorage.doesDirectoryExist(*) shouldReturn future(false)
      fileStorage.doesExist(*) shouldReturn future(false)
      fileStorage.moveDirectory(oldPath, newPath) shouldReturn future(directory)

      whenReady(service.moveFolder(project.id, oldPath, newPath))(_ shouldBe directory.asRight)
    }

    "return error if Project is not in Idle mode" in new Setup {
      val projectIsNotInIdleMode = project.copy(entity = project.entity.copy(status = Interactive))
      dao.get(project.id) shouldReturn future(Some(projectIsNotInIdleMode))

      whenReady(service.moveFolder(project.id, oldPath, newPath)) {
        _ shouldBe DCProjectServiceError.ProjectIsNotInIdleMode.asLeft
      }
    }

    "return error when folder is not empty" in new Setup {
      dao.get(project.id) shouldReturn future(Some(project))
      fileStorage.path(*, eqTo(project.entity.basePath, oldPath)) shouldReturn oldPath
      fileStorage.listDirectory(oldPath, recursive = false) shouldReturn future(List(File("file1", 256L, Instant.now)))

      whenReady(service.moveFolder(project.id, oldPath, newPath)) {
        _ shouldBe DCProjectServiceError.FolderIsNotEmpty.asLeft
      }
    }

    "return error when file already exists in the target (new) path" in new Setup {
      dao.get(project.id) shouldReturn future(Some(project))
      fileStorage.path(*, eqTo(project.entity.basePath, oldPath)) shouldReturn oldPath
      fileStorage.listDirectory(oldPath, recursive = false) shouldReturn future(List.empty)
      fileStorage.path(*, eqTo(project.entity.basePath, newPath)) shouldReturn newPath
      fileStorage.doesDirectoryExist(*) shouldReturn future(false)
      fileStorage.doesExist(*) shouldReturn future(true)

      whenReady(service.moveFolder(project.id, oldPath, newPath)) {
        _ shouldBe DCProjectServiceError.ObjectAlreadyExists.asLeft
      }
    }

  }

  "DCProjectService#removeObject" should {

    val path = "path/to/folder"

    "remove folder" in new Setup {
      dao.get(project.id) shouldReturn future(Some(project))
      fileStorage.path(*, eqTo(project.entity.basePath, path)) shouldReturn path
      fileStorage.doesDirectoryExist(*) shouldReturn future(true)
      fileStorage.doesExist(*) shouldReturn future(false)
      fileStorage.deleteDirectory(path) shouldReturn future(())

      whenReady(service.removeObject(project.id, path)) { result =>
        result shouldBe ().asRight
        fileStorage.deleteDirectory(path) wasCalled once
      }
    }

    "return error when Project is not in Idle mode" in new Setup {
      val projectIsNotInIdleMode = project.copy(entity = project.entity.copy(status = Interactive))
      dao.get(project.id) shouldReturn future(Some(projectIsNotInIdleMode))

      whenReady(service.removeObject(project.id, path)) { result =>
        result shouldBe DCProjectServiceError.ProjectIsNotInIdleMode.asLeft
      }
    }

    "remove file" in new Setup {
      dao.get(project.id) shouldReturn future(Some(project))
      fileStorage.path(*, eqTo(project.entity.basePath, path)) shouldReturn path
      fileStorage.doesDirectoryExist(*) shouldReturn future(false)
      fileStorage.doesExist(*) shouldReturn future(true)
      fileStorage.delete(path) shouldReturn future(())

      whenReady(service.removeObject(project.id, path)) { result =>
        result shouldBe ().asRight
        fileStorage.delete(path) wasCalled once
      }
    }

    "return error when project is not in Idle mode" in new Setup {
      val projectIsNotInIdleMode = project.copy(entity = project.entity.copy(status = Interactive))
      dao.get(project.id) shouldReturn future(Some(projectIsNotInIdleMode))

      whenReady(service.removeObject(project.id, path)) { result =>
        result shouldBe DCProjectServiceError.ProjectIsNotInIdleMode.asLeft
      }
    }

    "return error when object was not found" in new Setup {
      dao.get(project.id) shouldReturn future(Some(project))
      fileStorage.path(*, eqTo(project.entity.basePath, path)) shouldReturn path
      fileStorage.doesDirectoryExist(*) shouldReturn future(false)
      fileStorage.doesExist(*) shouldReturn future(false)

      whenReady(service.removeObject(project.id, path))(_ shouldBe DCProjectServiceError.ObjectNotFound.asLeft)
    }

  }

  "DCProjectService#delete" should {

    "cancel session followed by deleting DC project successfully" in new Setup {
      dao.get(project.id) shouldReturn future(Some(project))
      dao.delete(project.id) shouldReturn future(true)
      projectService.removeAssetFromAllProjects(AssetReference(project.id, AssetType.DCProject)) shouldReturn future(())
      processService.cancelProcesses(project.id, AssetType.DCProject) shouldReturn future(Right(()))
      assetSharingService.deleteSharesForAsset(any[String], eqTo(AssetType.DCProject)) shouldReturn future(())
      sessionService.cancel(project) shouldReturn future(Right(()))
      packageDao.updateMany(
        eqTo(DCProjectPackageDao.DCProjectIdIs(project.id)),
        *
      ) shouldReturn future(1)

      whenReady(service.delete(project.id)) {
        _ shouldBe Right(())
      }
    }

    "be unable to delete non-existing DC project" in new Setup {
      dao.get(project.id) shouldReturn future(None)

      whenReady(service.delete(project.id)) {
        _ shouldBe Left(DCProjectNotFound)
      }
    }

    "be unable to delete when user is not owner of DC project" in new Setup {
      dao.get(project.id) shouldReturn future(Some(project))

      whenReady(service.delete(project.id)(user = SampleUser.copy(id = UUID.randomUUID))) {
        _ shouldBe Left(AccessDenied)
      }
    }
  }
}
