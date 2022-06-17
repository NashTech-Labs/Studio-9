package baile.services.dcproject

import java.time.Instant
import java.util.UUID

import akka.NotUsed
import akka.event.LoggingAdapter
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import baile.dao.dcproject.{ DCProjectDao, DCProjectPackageDao }
import baile.daocommons.WithId
import baile.daocommons.filters.Filter
import baile.daocommons.sorting.Field
import baile.domain.asset.AssetType
import baile.domain.dcproject.DCProjectStatus.Idle
import baile.domain.dcproject.{ DCProject, DCProjectStatus }
import baile.domain.usermanagement.User
import baile.services.asset.AssetService
import baile.services.asset.AssetService.{
  WithOwnershipTransfer,
  AssetCreateErrors,
  AssetCreateParams,
  WithCreate,
  WithNestedUsageTracking,
  WithProcess,
  WithSharedAccess
}
import baile.services.asset.sharing.AssetSharingService
import baile.services.common.EntityService.WithSortByField
import baile.services.dcproject.DCProjectService.{ DCProjectServiceError, ObjectSearchResult }
import baile.services.process.ProcessService
import baile.services.project.ProjectService
import baile.services.remotestorage.{ Directory, File, RemoteStorageService, StoredObject }
import baile.utils.validation.Option._
import cats.data.EitherT
import cats.implicits._
import com.typesafe.config.Config

import scala.concurrent.{ ExecutionContext, Future }

class DCProjectService(
  protected val dao: DCProjectDao,
  protected val sessionService: SessionService,
  protected val projectService: ProjectService,
  protected val fileStorage: RemoteStorageService,
  protected val conf: Config,
  protected val processService: ProcessService,
  protected val assetSharingService: AssetSharingService,
  protected val packageDao: DCProjectPackageDao
)(
  implicit val ec: ExecutionContext,
  val logger: LoggingAdapter
) extends AssetService[DCProject, DCProjectServiceError]
  with WithSortByField[DCProject, DCProjectServiceError]
  with WithProcess[DCProject, DCProjectServiceError]
  with WithSharedAccess[DCProject, DCProjectServiceError]
  with WithNestedUsageTracking[DCProject, DCProjectServiceError]
  with WithOwnershipTransfer[DCProject]
  with WithCreate[DCProject, DCProjectServiceError, DCProjectServiceError] {

  import baile.services.dcproject.DCProjectService.DCProjectServiceError._

  override val assetType: AssetType = AssetType.DCProject
  override val forbiddenError: DCProjectServiceError = AccessDenied
  override val sortingFieldNotFoundError: DCProjectServiceError = SortingFieldUnknown
  override val notFoundError: DCProjectServiceError = DCProjectNotFound
  override val inUseError: DCProjectServiceError = DCProjectInUse

  override protected val createErrors: AssetCreateErrors[DCProjectServiceError] = DCProjectServiceError
  override protected val findField: String => Option[Field] = Map(
    "name" -> DCProjectDao.Name,
    "created" -> DCProjectDao.Created,
    "updated" -> DCProjectDao.Updated
  ).get

  override def updateOwnerId(dcProject: DCProject, ownerId: UUID): DCProject = dcProject.copy(ownerId = ownerId)

  private val storageKeyPrefix: String = conf.getString("storage-prefix")

  def create(
    name: Option[String],
    description: Option[String]
  )(implicit user: User): Future[Either[DCProjectServiceError, WithId[DCProject]]] = {

    def createDCProject(createParams: AssetCreateParams): Future[WithId[DCProject]] = {
      val now = Instant.now()
      dao.create(id =>
        DCProject(
          name = createParams.name,
          created = now,
          updated = now,
          ownerId = user.id,
          status = DCProjectStatus.Idle,
          description = description,
          basePath = s"projects/$id",
          packageName = None,
          latestPackageVersion = None
        )
      )
    }
    val result = for {
      createParams <- validateAndGetAssetCreateParams(name, None)
      project <- EitherT.right[DCProjectServiceError](createDCProject(createParams))
    } yield project

    result.value

  }

  def update(
    id: String,
    newName: Option[String],
    newDescription: Option[String]
  )(implicit user: User): Future[Either[DCProjectServiceError, WithId[DCProject]]] = {

    this.update(
      id,
      _ => newName.validate(name => validateAssetName[DCProjectServiceError](
        name,
        Option(id),
        EmptyProjectName,
        NameIsTaken
      )),
      project => project.copy(
        name = newName.getOrElse(project.name),
        updated = Instant.now(),
        description = newDescription orElse project.description
      )
    )
  }

  def updateFile(
    id: String,
    path: String,
    lastModified: Instant,
    content: Source[ByteString, Any]
  )(implicit user: User, materializer: Materializer): Future[Either[DCProjectServiceError, File]] = {
    val result = for {
      project <- EitherT(get(id))
      _ <- EitherT.cond[Future](project.entity.status == Idle, (), ProjectIsNotInIdleMode)
      fullPath = buildFullStoragePath(project.entity, path)
      searchResult <- EitherT.right[DCProjectServiceError](searchForObject(fullPath))
      _ <- EitherT.fromEither[Future](searchResult match {
        case ObjectSearchResult.DirectoryExists => DCProjectServiceError.ObjectNotFile.asLeft
        case ObjectSearchResult.FileExists => ().asRight
        case ObjectSearchResult.ObjectDoesNotExist => DCProjectServiceError.ObjectNotFound.asLeft
      })
      targetLastModified <- EitherT.right[DCProjectServiceError](fileStorage.readMeta(fullPath).map(_.lastModified))
      _ <- EitherT.cond[Future](
        lastModified == targetLastModified,
        (),
        DCProjectServiceError.FileWasUpdated
      )
      updatedFile <- EitherT.right[DCProjectServiceError](fileStorage.write(content, fullPath))
    } yield stripFilePrefix(updatedFile, project.entity)

    result.value
  }

  def createFile(
    id: String,
    path: String,
    content: Source[ByteString, Any]
  )(implicit user: User, materializer: Materializer): Future[Either[DCProjectServiceError, File]] = {
    val result = for {
      project <- EitherT(get(id))
      _ <- EitherT.cond[Future](project.entity.status == Idle, (), ProjectIsNotInIdleMode)
      _ <- validateIntermediateFolders(path, project.entity)
      fullPath = buildFullStoragePath(project.entity, path)
      _ <- EitherT(validateObjectDoesNotExist(fullPath))
      file <- EitherT.right[DCProjectServiceError](fileStorage.write(content, fullPath))
    } yield stripFilePrefix(file, project.entity)

    result.value
  }

  def copyFile(
    id: String,
    oldPath: String,
    newPath: String
  )(implicit user: User): Future[Either[DCProjectServiceError, File]] = {
    val result = for {
      project <- EitherT(get(id))
      _ <- EitherT.cond[Future](project.entity.status == Idle, (), ProjectIsNotInIdleMode)
      _ <- validateIntermediateFolders(newPath, project.entity)
      fullOldPath = buildFullStoragePath(project.entity, oldPath)
      fileExists <- EitherT.right[DCProjectServiceError](fileStorage.doesExist(fullOldPath))
      _ <- EitherT.cond[Future].apply[DCProjectServiceError, Unit](
        fileExists,
        (),
        DCProjectServiceError.ObjectNotFound
      )
      fullNewPath = buildFullStoragePath(project.entity, newPath)
      _ <- EitherT(validateObjectDoesNotExist(fullNewPath))
      file <- EitherT.right[DCProjectServiceError](fileStorage.copy(fullOldPath, fullNewPath))
    } yield stripFilePrefix(file, project.entity)

    result.value
  }

  def moveFile(
    id: String,
    oldPath: String,
    newPath: String
  )(implicit user: User): Future[Either[DCProjectServiceError, File]] = {
    val result = for {
      project <- EitherT(get(id))
      _ <- EitherT.cond[Future](project.entity.status == Idle, (), ProjectIsNotInIdleMode)
      _ <- validateIntermediateFolders(newPath, project.entity)
      fullOldPath = buildFullStoragePath(project.entity, oldPath)
      fileExists <- EitherT.right[DCProjectServiceError](fileStorage.doesExist(fullOldPath))
      _ <- EitherT.cond[Future].apply[DCProjectServiceError, Unit](
        fileExists,
        (),
        DCProjectServiceError.ObjectNotFound
      )
      fullNewPath = buildFullStoragePath(project.entity, newPath)
      _ <- EitherT(validateObjectDoesNotExist(fullNewPath))
      file <- EitherT.right[DCProjectServiceError](fileStorage.move(fullOldPath, fullNewPath))
    } yield stripFilePrefix(file, project.entity)

    result.value
  }

  def getFile(
    id: String,
    path: String
  )(implicit user: User): Future[Either[DCProjectServiceError, File]] = {
    val result = for {
      project <- EitherT(get(id))
      fullPath = buildFullStoragePath(project.entity, path)
      fileExists <- EitherT.right[DCProjectServiceError](fileStorage.doesExist(fullPath))
      _ <- EitherT.cond[Future].apply[DCProjectServiceError, Unit](
        fileExists,
        (),
        DCProjectServiceError.ObjectNotFound
      )
      file <- EitherT.right[DCProjectServiceError](fileStorage.readMeta(fullPath))
    } yield file

    result.value
  }

  def getFileContent(
    id: String,
    path: String
  )(implicit user: User): Future[Either[DCProjectServiceError, Source[ByteString, NotUsed]]] = {
    val result = for {
      project <- EitherT(get(id))
      fullPath = buildFullStoragePath(project.entity, path)
      fileExists <- EitherT.right[DCProjectServiceError](fileStorage.doesExist(fullPath))
      _ <- EitherT.cond[Future].apply[DCProjectServiceError, Unit](
        fileExists,
        (),
        DCProjectServiceError.ObjectNotFound
      )
      streamedFile <- EitherT.right[DCProjectServiceError](fileStorage.streamFile(fullPath))
    } yield streamedFile.content

    result.value
  }

  def createFolder(
    id: String,
    path: String
  )(implicit user: User): Future[Either[DCProjectServiceError, Directory]] = {
    val result = for {
      project <- EitherT(get(id))
      _ <- EitherT.cond[Future](project.entity.status == Idle, (), ProjectIsNotInIdleMode)
      _ <- validateIntermediateFolders(path, project.entity)
      fullPath = buildFullStoragePath(project.entity, path)
      _ <- EitherT(validateObjectDoesNotExist(fullPath))
      directory <- EitherT.right[DCProjectServiceError](fileStorage.createDirectory(fullPath))
    } yield stripDirectoryPrefix(directory, project.entity)

    result.value
  }

  def listFolder(
    id: String,
    path: Option[String],
    recursive: Boolean,
    sharedResourceId: Option[String] = None
  )(implicit user: User): Future[Either[DCProjectServiceError, List[StoredObject]]] = {
    val result = for {
      project <- EitherT(get(id, sharedResourceId))
      objectsPrefix = buildFullStoragePath(project.entity, path.getOrElse(""))
      storedObjects <- EitherT.right[DCProjectServiceError](
        fileStorage.listDirectory(objectsPrefix, recursive = recursive)
      )
    } yield storedObjects.map { storedObject =>
      storedObject.updatePath(storedObject.path.stripPrefix(objectsPrefix + "/"))
    }

    result.value
  }

  def moveFolder(
    id: String,
    oldPath: String,
    newPath: String
  )(implicit user: User): Future[Either[DCProjectServiceError, Directory]] = {
    val result = for {
      project <- EitherT(get(id))
      _ <- EitherT.cond[Future](project.entity.status == Idle, (), ProjectIsNotInIdleMode)
      fullOldPath = buildFullStoragePath(project.entity, oldPath)
      objectsInFolder <- EitherT.right[DCProjectServiceError](
        fileStorage.listDirectory(fullOldPath, recursive = false)
      )
      _ <- EitherT.cond[Future](
        objectsInFolder.isEmpty,
        (),
        DCProjectServiceError.FolderIsNotEmpty
      )
      fullNewPath = buildFullStoragePath(project.entity, newPath)
      _ <- EitherT(validateObjectDoesNotExist(fullNewPath))
      directory <- EitherT.right[DCProjectServiceError](fileStorage.moveDirectory(fullOldPath, fullNewPath))
    } yield stripDirectoryPrefix(directory, project.entity)

    result.value
  }

  def removeObject(
    id: String,
    path: String
  )(implicit user: User): Future[Either[DCProjectServiceError, Unit]] = {

    def removeFileOrFolderIfFound(project: DCProject): Future[Either[DCProjectServiceError, Unit]] = {
      val fullPath = buildFullStoragePath(project, path)
      for {
        searchResult <- searchForObject(fullPath)
        result <- searchResult match {
          case ObjectSearchResult.DirectoryExists => fileStorage.deleteDirectory(fullPath).map(_.asRight)
          case ObjectSearchResult.FileExists => fileStorage.delete(fullPath).map(_.asRight)
          case ObjectSearchResult.ObjectDoesNotExist => Future.successful(DCProjectServiceError.ObjectNotFound.asLeft)
        }
      } yield result
    }

    val result = for {
      project <- EitherT(get(id))
      _ <- EitherT.cond[Future](project.entity.status == Idle, (), ProjectIsNotInIdleMode)
      _ <- EitherT(removeFileOrFolderIfFound(project.entity))
    } yield ()

    result.value
  }

  override protected def preDelete(
    project: WithId[DCProject]
  )(implicit user: User): Future[Either[DCProjectServiceError, Unit]] = {
    val result = for {
      _ <- EitherT(super.preDelete(project))
      _ <- EitherT.right[DCProjectServiceError](sessionService.cancel(project).map(_.getOrElse(())))
      _ <- EitherT.right[DCProjectServiceError](unlinkProjectPackages(project.id))
    } yield ()

    result.value
  }

  private[services] def update(
    dcProjectId: String,
    status: DCProjectStatus
  ): Future[Option[WithId[DCProject]]] =
    dao.update(dcProjectId, _.copy(
      status = status
    ))

  private[services] def update(
    dcProjectId: String,
    status: DCProjectStatus,
    packageName: Option[String]
  ): Future[Option[WithId[DCProject]]] =
    dao.update(dcProjectId, _.copy(
      status = status,
      packageName = packageName
    ))

  private[services] def buildFullStoragePath(project: DCProject, path: String): String =
    fileStorage.path(storageKeyPrefix, project.basePath, path)

  private def validateObjectDoesNotExist(fullPath: String): Future[Either[DCProjectServiceError, Unit]] =
    searchForObject(fullPath).map {
      case ObjectSearchResult.ObjectDoesNotExist => ().asRight
      case _ => ObjectAlreadyExists.asLeft
    }

  private def stripDirectoryPrefix(directory: Directory, project: DCProject): Directory =
    stripStoredObjectPrefix(directory, project)(directory.updatePath)

  private def stripFilePrefix(file: File, project: DCProject): File =
    stripStoredObjectPrefix(file, project)(file.updatePath)

  private def stripStoredObjectPrefix[T <: StoredObject](t: T, project: DCProject)(f: String => T): T =
    f(t.path.stripPrefix(buildFullStoragePath(project, "") + "/"))

  private def searchForObject(fullPath: String): Future[ObjectSearchResult] = {
    val doesFileExist = fileStorage.doesExist(fullPath)
    val doesFolderExist = fileStorage.doesDirectoryExist(fullPath)

    for {
      fileExists <- doesFileExist
      folderExists <- doesFolderExist
    } yield (fileExists, folderExists) match {
      case (false, false) => ObjectSearchResult.ObjectDoesNotExist
      case (true, false) => ObjectSearchResult.FileExists
      case (false, true) => ObjectSearchResult.DirectoryExists
      case (true, true) => throw new RuntimeException(s"Both file and directory exist on path $fullPath")
    }

  }

  private[dcproject] def count(filter: Filter): Future[Int] =
    dao.count(filter)

  private def unlinkProjectPackages(projectId: String): Future[Int] = {
    packageDao.updateMany(
      DCProjectPackageDao.DCProjectIdIs(projectId),
      _.copy(dcProjectId = None)
    )
  }


  private def validateIntermediateFolders(
    path: String,
    project: DCProject
  ): EitherT[Future, DCProjectServiceError, Unit] = {

    def validatePathExists(listOfsubPaths: List[String]): EitherT[Future, DCProjectServiceError, Unit] = {

      def validateRemainingPathsIfObjectExists(
        folderPath: String,
        objectSearchResult: ObjectSearchResult,
        remainingPaths: List[String]
      ): EitherT[Future, DCProjectServiceError, Unit] =
        objectSearchResult match {
          case ObjectSearchResult.DirectoryExists => validatePathExists(remainingPaths)
          case _ => EitherT.leftT[Future, Unit](PathNotFound(folderPath): DCProjectServiceError)
        }

      listOfsubPaths match {
        case folderPath :: remainingPaths =>
          val fullPath = buildFullStoragePath(project, folderPath)
          for {
            searchResult <- EitherT.right[DCProjectServiceError](searchForObject(fullPath))
            _ <- validateRemainingPathsIfObjectExists(folderPath, searchResult, remainingPaths)
          } yield ()
        case _ => EitherT.rightT[Future, DCProjectServiceError](())
      }
    }

    val foldersPath = getIntermediateFolderPaths(path)
    validatePathExists(foldersPath)
  }

  private def getIntermediateFolderPaths(path: String): List[String] = {

    def getSubPaths(fromIndex: Int, subPaths: List[String]): List[String] = {
      val index = path.indexOf("/", fromIndex + 1)
      if (index != -1) getSubPaths(index, subPaths :+ path.substring(0, index))
      else subPaths
    }

    getSubPaths(0, List.empty[String])
  }

}

object DCProjectService {

  sealed trait DCProjectServiceError

  object DCProjectServiceError extends AssetCreateErrors[DCProjectServiceError] {

    case object AssetNotFound extends DCProjectServiceError

    case object AccessDenied extends DCProjectServiceError

    case object DCProjectNotFound extends DCProjectServiceError

    case object SortingFieldUnknown extends DCProjectServiceError

    case object NameNotSpecified extends DCProjectServiceError

    case object EmptyProjectName extends DCProjectServiceError

    case object NameIsTaken extends DCProjectServiceError

    case object FileWasUpdated extends DCProjectServiceError

    case object ObjectAlreadyExists extends DCProjectServiceError

    case object ObjectNotFile extends DCProjectServiceError

    case object ObjectNotFound extends DCProjectServiceError

    case object FolderIsNotEmpty extends DCProjectServiceError

    case object ProjectIsNotInIdleMode extends DCProjectServiceError

    case class PathNotFound(path: String) extends DCProjectServiceError

    case object DCProjectInUse extends DCProjectServiceError

    override val nameNotSpecifiedError: DCProjectServiceError = NameNotSpecified
    override val emptyNameError: DCProjectServiceError = EmptyProjectName

    override def nameAlreadyExistsError(name: String): DCProjectServiceError = NameIsTaken
  }

  private sealed trait ObjectSearchResult

  private object ObjectSearchResult {

    case object DirectoryExists extends ObjectSearchResult

    case object FileExists extends ObjectSearchResult

    case object ObjectDoesNotExist extends ObjectSearchResult

  }

}
