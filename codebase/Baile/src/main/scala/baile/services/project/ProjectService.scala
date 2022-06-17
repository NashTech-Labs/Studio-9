package baile.services.project

import java.time.Instant

import akka.event.LoggingAdapter
import baile.dao.project.ProjectDao
import baile.dao.project.ProjectDao.{ NameIs, OwnerIdIs }
import baile.daocommons.WithId
import baile.daocommons.filters.{ Filter, IdIs }
import baile.domain.asset.{ Asset, AssetReference }
import baile.domain.project.{ Folder, Project, ProjectAssetReference }
import baile.domain.usermanagement.User
import baile.services.asset.AssetService
import baile.services.common.EntityService.WithSortByField
import baile.services.common.{ EntityService, EntityUpdateFailedException }
import baile.services.project.ProjectService.ProjectServiceError._
import baile.services.project.ProjectService.{ ProjectServiceCreateError, ProjectServiceError }
import cats.data.EitherT
import baile.utils.validation.Option._
import cats.implicits._

import scala.concurrent.{ ExecutionContext, Future }

class ProjectService(val dao: ProjectDao)(
  implicit val ec: ExecutionContext,
  val logger: LoggingAdapter
) extends EntityService[Project, ProjectServiceError] {

  override val notFoundError: ProjectServiceError = ProjectNotFound

  def create(name: String)(implicit user: User): Future[Either[ProjectServiceCreateError, WithId[Project]]] = {

    def prepareProject: Project = {
      val now = Instant.now()
      Project(
        name = name,
        created = now,
        updated = now,
        ownerId = user.id,
        folders = Seq.empty,
        assets = Seq.empty
      )
    }

    val result = for {
      _ <- EitherT(ensureNameUnique(NameIs(name) && OwnerIdIs(user.id), name)).leftMap(_ =>
        ProjectServiceCreateError.ProjectNameAlreadyExists(name)
      )
      projectObject = prepareProject
      projectId <- EitherT.right[ProjectServiceCreateError](dao.create(projectObject))
    } yield WithId(projectObject, projectId)

    result.value

  }

  def listAll(implicit user: User): Future[(Seq[WithId[Project]], Int)] = {
    for {
      projects <- dao.listAll(OwnerIdIs(user.id))
      count <- dao.count(OwnerIdIs(user.id))
    } yield (projects, count)
  }

  def update(
    id: String,
    newName: String
  )(implicit user: User): Future[Either[ProjectServiceError, WithId[Project]]] = {

    val result = for {
      _ <- EitherT(ensureNameUnique(NameIs(newName) && OwnerIdIs(user.id) && !IdIs(id), newName))
      response <- EitherT(update(id, _.copy(name = newName)))
    } yield response

    result.value
  }

  def addAsset[T <: Asset[_]](
    projectId: String,
    folderId: Option[String],
    assetId: String,
    assetService: AssetService[T, _]
  )(implicit user: User): Future[Either[ProjectServiceError, Unit]] = {

    def removeFolderLinkIfExist(project: Project): Future[Unit] = {
      project.assets.find(_.assetReference.id == assetId).map{ projectAssetReference =>
        dao.removeAsset(projectId, projectAssetReference.assetReference).map(_ => ())
      }.getOrElse(Future.successful(()))
    }

    val assetReference = AssetReference(assetId, assetService.assetType)
    val assetFolderReference = ProjectAssetReference(assetReference, folderId)
    val result = for {
      _ <- EitherT(ensureAssetOwnerShip(assetId, assetService))
      projectWithId <- EitherT(get(projectId))
      _ <- EitherT.fromEither[Future](folderId.validate(id => getFolderFromProject(projectWithId, id).map(_ => ())))
      _ <- EitherT.fromEither[Future](ensureAssetDoesNotExist(projectWithId.entity, assetReference))
      _ <- EitherT.right(removeFolderLinkIfExist(projectWithId.entity))
      updateResult <- EitherT.right[ProjectServiceError](
        dao.addAsset(projectWithId.id, assetFolderReference)
      )
      _ <- EitherT.rightT[Future, ProjectServiceError](
        updateResult.getOrElse(throw EntityUpdateFailedException(projectWithId.id, projectWithId.entity))
      )
    } yield ()

    result.value
  }

  def createFolder(
    projectId: String,
    folderPath: String
  )(implicit user: User): Future[Either[ProjectServiceError, WithId[Folder]]] = {

    def ensureFolderPathIsUnique(project: Project): Either[ProjectServiceError, Unit] = Either.cond(
      !project.folders.exists(_.entity.path == folderPath),
      (),
      FolderPathIsDuplicate
    )

    def ensureParentExist(project: Project): Either[ProjectServiceError, Unit] = {
      val parent = folderPath.take(folderPath.lastIndexOf('/'))
      Either.cond(
        project.folders.exists(_.entity.path == parent) || parent.isEmpty,
        (),
        FolderParentNotExist
      )
    }

    val folder = Folder(folderPath)
    val result = for {
      projectWithId <- EitherT(get(projectId))
      _ <- EitherT.fromEither[Future](ensureFolderPathIsUnique(projectWithId.entity))
      _ <- EitherT.fromEither[Future](ensureParentExist(projectWithId.entity))
      updateResult <- EitherT.right[ProjectServiceError](dao.addFolder(projectId, folder))
      newFolder <- EitherT.rightT[Future, ProjectServiceError](
        updateResult.getOrElse(throw EntityUpdateFailedException(projectWithId.id, projectWithId.entity))
      )
    } yield newFolder
    result.value
  }

  def getFolder(
    projectId: String,
    folderId: String
  )(implicit user: User): Future[Either[ProjectServiceError, WithId[Folder]]] = {
    val result = for {
      projectWithId <- EitherT(get(projectId))
      folderWithId <- EitherT.fromEither[Future](getFolderFromProject(projectWithId, folderId))
    } yield folderWithId
    result.value
  }

  def deleteFolder(
    projectId: String,
    folderId: String
  )(implicit user: User): Future[Either[ProjectServiceError, Unit]] = {

    def getAssetFolderReferences(project: Project): Seq[ProjectAssetReference] = {
      project.assets.filter(_.folderId.contains(folderId))
    }

    def removeReferences(assetFolderReferences: Seq[ProjectAssetReference]): Future[Unit] = {
      Future.sequence(
        assetFolderReferences.map { projectAssetReference =>
          dao.removeAsset(projectId, projectAssetReference.assetReference)
        }
      ).map(_ => ())
    }

    val result = for {
      projectWithId <- EitherT(get(projectId))
      _ <- EitherT.fromEither[Future](getFolderFromProject(projectWithId, folderId))
      assets = getAssetFolderReferences(projectWithId.entity)
      _ <- EitherT.right[ProjectServiceError](removeReferences(assets))
      _ <- EitherT.right[ProjectServiceError](dao.removeFolder(projectId, folderId))
    } yield ()
    result.value
  }

  def deleteAsset[T <: Asset[_]](
    projectId: String,
    assetId: String,
    assetService: AssetService[T, _]
  )(implicit user: User): Future[Either[ProjectServiceError, Unit]] = {
    val assetReference = AssetReference(assetId, assetService.assetType)
    val result = for {
      projectWithId <- EitherT(get(projectId))
      _ <- EitherT.fromEither[Future](ensureAssetExists(projectWithId.entity, assetReference))
      updateResult <- EitherT.right[ProjectServiceError](dao.removeAsset(
        projectWithId.id, assetReference
      ))
      _ <- EitherT.rightT[Future, ProjectServiceError](updateResult.getOrElse(
        throw EntityUpdateFailedException(projectWithId.id, projectWithId.entity)
      ))
    } yield ()

    result.value
  }

  override protected def ensureCanRead(
    project: WithId[Project],
    user: User
  ): Future[Either[ProjectServiceError, Unit]] = {
    ensureOwnership(project, user)
  }

  override protected def ensureCanDelete(
    project: WithId[Project],
    user: User
  ): Future[Either[ProjectServiceError, Unit]] = {
    ensureOwnership(project, user)
  }

  override protected def ensureCanUpdate(
    project: WithId[Project],
    user: User
  ): Future[Either[ProjectServiceError, Unit]] = {
    ensureOwnership(project, user)
  }

  private[services] def removeAssetFromAllProjects(
    assetReference: AssetReference
  )(implicit user: User): Future[Unit] = {
    dao.removeAssetFromAllProjects(assetReference, user.id)
  }

  private[project] def ensureOwnership(
    project: WithId[Project],
    user: User
  ): Future[Either[ProjectServiceError, Unit]] = {
    Future.successful {
      if (project.entity.ownerId == user.id) ().asRight
      else AccessDenied.asLeft
    }
  }

  private def translateError[T <: Asset[_], F](error: F, assetService: AssetService[T, F]): ProjectServiceError =
    (error, assetService) match {
      case (assetService.forbiddenError, _) => AccessDenied
      case (assetService.notFoundError, _) => AssetNotFound
      case (_, service: WithSortByField[_, _]) if error == service.sortingFieldNotFoundError => SortingFieldUnknown
      case _ => throw new RuntimeException(s"Unexpected error response $error from service ${ assetService.getClass }")
    }

  private def ensureNameUnique(filter: Filter, projectName: String): Future[Either[ProjectServiceError, Unit]] = {
    dao.count(filter).map { count =>
      if (count > 0) ProjectNameAlreadyExists(projectName).asLeft else ().asRight
    }
  }

  private def ensureAssetDoesNotExist(
    project: Project,
    assetReference: AssetReference
  ): Either[ProjectServiceError, Unit] = {
    ensureAssetExists(project, assetReference) match {
      case Right(_) => AssetAlreadyExistsInProject.asLeft
      case Left(_) => ().asRight
    }
  }

  private def ensureAssetOwnerShip[T <: Asset[_], F](
    assetId: String,
    assetService: AssetService[T, F]
  )(implicit user: User): Future[Either[ProjectServiceError, Unit]] =
    assetService.get(assetId).map(_.bimap(
      error => translateError(error, assetService),
      _ => ()
    ))

  private def getFolderFromProject(
    projectWithId: WithId[Project],
    folderId: String
  ): Either[ProjectServiceError, WithId[Folder]] = {
    Either.fromOption(
      projectWithId.entity.folders.find(_.id == folderId), ProjectServiceError.FolderNotFound
    )
  }

  private def ensureAssetExists(
    project: Project,
    assetReference: AssetReference
  ): Either[ProjectServiceError, Unit] =
    Either.cond(
      project.assets.map(_.assetReference).contains(assetReference),
      (),
      AssetNotFound
    )

}

object ProjectService {

  sealed trait ProjectServiceCreateError

  sealed trait ProjectServiceError

  object ProjectServiceCreateError {

    case class ProjectNameAlreadyExists(name: String) extends ProjectServiceCreateError

  }

  object ProjectServiceError {

    case class ProjectNameAlreadyExists(name: String) extends ProjectServiceError

    case object AssetNotFound extends ProjectServiceError

    case object AccessDenied extends ProjectServiceError

    case object ProjectNotFound extends ProjectServiceError

    case object SortingFieldUnknown extends ProjectServiceError

    case object AssetAlreadyExistsInProject extends ProjectServiceError

    case object FolderNotFound extends ProjectServiceError

    case object FolderPathIsDuplicate extends ProjectServiceError

    case object FolderParentNotExist extends ProjectServiceError

  }

}
