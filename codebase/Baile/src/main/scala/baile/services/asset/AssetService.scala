package baile.services.asset

import java.util.UUID

import baile.dao.asset.Filters._
import baile.daocommons.WithId
import baile.daocommons.filters._
import baile.domain.asset.sharing.SharedResource
import baile.domain.asset.{ Asset, AssetReference, AssetScope, AssetType }
import baile.domain.process.Process
import baile.domain.usermanagement.{ ExperimentExecutor, RegularUser, User }
import baile.services.asset.sharing.{ AssetSharingService, SharedAccessChecker }
import baile.services.common.EntityService
import baile.services.process.ProcessService
import baile.services.project.ProjectService
import baile.utils.UniqueNameGenerator
import baile.utils.validation.Option._
import cats.data.EitherT
import cats.implicits._

import scala.concurrent.{ ExecutionContext, Future }

trait AssetService[T <: Asset[_], F] extends EntityService[T, F] {

  val assetType: AssetType

  val forbiddenError: F

  protected val projectService: ProjectService

  def list(
    search: Option[String],
    orderBy: Seq[String],
    page: Int,
    pageSize: Int,
    projectId: Option[String],
    folderId: Option[String]
  )(implicit user: User): Future[Either[F, (Seq[WithId[T]], Int)]] = {
    val result = for {
      searchFilter <- EitherT.right[F](prepareListFilter(search, projectId, folderId))
      result <- EitherT(this.list(searchFilter, orderBy, page, pageSize))
    } yield result

    result.value
  }

  def count(
    search: Option[String],
    projectId: Option[String],
    folderId: Option[String]
  )(implicit user: User): Future[Either[F, Int]] = {
    val result = for {
      filter <- EitherT.right[F](prepareListFilter(search, projectId, folderId))
      count <- EitherT(this.count(filter))
    } yield count

    result.value
  }

  override protected def ensureCanRead(asset: WithId[T], user: User): Future[Either[F, Unit]] =
    ensureOwnership(asset, user)

  override protected def ensureCanUpdate(asset: WithId[T], user: User): Future[Either[F, Unit]] =
    ensureOwnership(asset, user)

  override protected def ensureCanDelete(asset: WithId[T], user: User): Future[Either[F, Unit]] =
    ensureOwnership(asset, user)

  override protected def prepareCanReadFilter(user: User): Future[Filter] =
    Future.successful(OwnerIdIs(user.id))

  protected def ensureOwnership(asset: WithId[T], user: User): Future[Either[F, Unit]] = {
    Future.successful {
      if (asset.entity.ownerId == user.id) ().asRight
      else forbiddenError.asLeft
    }
  }

  protected def prepareListFilter(
    search: Option[String],
    projectId: Option[String],
    folderId: Option[String]
  )(implicit user: User): Future[Filter] = {
    val inLibraryFilter = InLibraryIs(true)

    def buildProjectFilter(ids: Seq[String]): Filter = if (ids.isEmpty) FalseFilter else IdIn(ids)

    def getProjectAssetIds(pId: String): Future[Seq[String]] = {
      projectService.get(pId).map {
        case Right(project) =>
          folderId match {
            case Some(_) => project.entity.assets.filter { asset =>
              asset.folderId == folderId && asset.assetReference.`type` == assetType
            }.map(_.assetReference.id)
            case None => project.entity.assets.filter(_.folderId.isEmpty).map(_.assetReference.id)
          }
        case Left(_) => Seq.empty
      }
    }

    val searchFilter: Filter = search match {
      case Some(term) => inLibraryFilter && SearchQuery(term)
      case None => inLibraryFilter
    }

    val preparedProjectIdFilter: Future[Filter] = projectId match {
      case Some(pId) => getProjectAssetIds(pId).map(
        assetIds => buildProjectFilter(assetIds)
      )
      case None => Future.successful(TrueFilter)
    }
    preparedProjectIdFilter.map(searchFilter && _)
  }

  protected def sameNameFilter(name: String)(implicit user: User): Filter =
    NameIs(name) && OwnerIdIs(user.id) && InLibraryIs(true)

  // TODO change nameAlreadyExistsError type to String => R to make error more meaningful
  protected def validateAssetName[E](
    name: String,
    id: Option[String],
    emptyAssetNameError: E,
    nameAlreadyExistsError: E
  )(implicit user: User): Future[Either[E, Unit]] =
    if (name.isEmpty) {
      Future.successful(emptyAssetNameError.asLeft)
    } else {
      val basicFilter = sameNameFilter(name)
      val filter = id match {
        case Some(value) => basicFilter && !IdIs(value)
        case None => basicFilter
      }
      dao.count(filter).map { count =>
        if (count > 0) nameAlreadyExistsError.asLeft else ().asRight
      }
    }

}

object AssetService {

  trait WithProcess[T <: Asset[_], F] extends AssetService[T, F] { self =>

    protected val processService: ProcessService

    def getCurrentProcess(
      assetId: String
    )(implicit user: User): Future[Either[AssetProcessGetError, WithId[Process]]] = {
      val result = for {
        _ <- EitherT(get(assetId)).leftMap {
          case self.forbiddenError => AssetProcessGetError.AccessDenied
          case _ => AssetProcessGetError.AssetNotFound
        }
        process <- EitherT(processService.getProcess(assetId, assetType, None)).leftMap[AssetProcessGetError]{
          _ => AssetProcessGetError.ProcessNotFound
        }
      } yield process

      result.value
    }

    override protected def preDelete(
      asset: WithId[T]
    )(implicit user: User): Future[Either[F, Unit]] = {
      val result = for {
        _ <- EitherT(super.preDelete(asset))
        _ <- EitherT.right[F](projectService.removeAssetFromAllProjects(AssetReference(asset.id, assetType)))
        _ <- EitherT.right[F](processService.cancelProcesses(asset.id, assetType).map(_.getOrElse(())))
      } yield ()

      result.value
    }
  }

  trait WithOwnershipTransfer[T <: Asset[_]] {
    self: AssetService[T, _] =>

    implicit val ec: ExecutionContext

    def updateOwnerId(asset: T, ownerId: UUID): T

    def getAllAssetCount(userId: UUID): Future[Int] = dao.count(OwnerIdIs(userId))

    final def transferOwnership(from: UUID, to: UUID): Future[Unit] =
      dao.updateMany(OwnerIdIs(from), updateOwnerId(_, to)).map(_ => ())

  }

  // Maybe (yeah, maybe) we'll move this into AssetService completely, but keeping it as a mixin for now
  trait WithSharedAccess[T <: Asset[_], F] extends AssetService[T, F] with SharedAccessChecker { self =>

    protected val assetSharingService: AssetSharingService

    def get(
      id: String,
      sharedResourceIdOption: Option[String]
    )(implicit user: User): Future[Either[F, WithId[T]]] = sharedResourceIdOption match {
      case None => get(id)
      case Some(sharedResourceId) =>
        val result = for {
          withIdOption <- EitherT.right[F](dao.get(id))
          withId <- EitherT.fromEither[Future](ensureEntityFound(withIdOption))
          sharedResource <- EitherT(assetSharingService.get(sharedResourceId)).leftMap(_ => self.notFoundError)
          _ <- ensureCanRead(withId, user, sharedResource.entity)
        } yield withId

        result.value
    }

    def list(
      scope: Option[AssetScope],
      search: Option[String],
      orderBy: Seq[String],
      page: Int,
      pageSize: Int,
      projectId: Option[String],
      folderId: Option[String]
    )(implicit user: User): Future[Either[F, (Seq[WithId[T]], Int)]] = {
      val result = for {
        searchFilter <- EitherT.right[F](prepareListFilter(search, projectId, folderId))
        canReadFilter <- EitherT.right[F](prepareCanReadFilter(scope, user))
        sortBy <- EitherT.fromEither[Future](prepareSortBy(orderBy))
        items <- EitherT.right[F](dao.list(canReadFilter && searchFilter, page, pageSize, sortBy))
        count <- EitherT.right[F](dao.count(canReadFilter && searchFilter))
      } yield (items, count)

      result.value
    }

    def count(
      scope: Option[AssetScope],
      search: Option[String],
      projectId: Option[String],
      folderId: Option[String]
    )(implicit user: User): Future[Either[F, Int]] = {
      val result = for {
        searchFilter <- EitherT.right[F](prepareListFilter(search, projectId, folderId))
        canReadFilter <- EitherT.right[F](prepareCanReadFilter(scope, user))
        count <- EitherT.right[F](dao.count(canReadFilter && searchFilter))
      } yield count

      result.value
    }

    protected def prepareCanReadFilter(scope: Option[AssetScope], user: User): Future[Filter] = scope match {
      case Some(AssetScope.Personal) | None => prepareCanReadFilter(user)
      case Some(AssetScope.Shared) => prepareSharedFilter(user)
      case Some(AssetScope.All) => for {
        sharedFilter <- prepareSharedFilter(user)
        canReadFilter <- prepareCanReadFilter(user)
      } yield sharedFilter || canReadFilter
    }

    final protected def prepareSharedFilter(user: User): Future[Filter] =
      assetSharingService.listAll(user.id, assetType).map { sharedResources =>
        IdIn(sharedResources.map(_.entity.assetId))
      }

    private def ensureCanRead(
      withId: WithId[T],
      user: User,
      sharedResource: SharedResource
    ): EitherT[Future, F, Unit] = {
      EitherT(ensureCanRead(withId, user)) orElse {
        checkSharedAccess(AssetReference(withId.id, assetType), sharedResource)
          .leftMap(_ => forbiddenError)
      }
    }

    def checkSharedAccess(
      assetReference: AssetReference,
      sharedResource: SharedResource
    ): EitherT[Future, Unit, Unit] =
      accessGrantedIf(sharedResource.assetType == assetReference.`type` && sharedResource.assetId == assetReference.id)

    override protected def preDelete(
      entity: WithId[T]
    )(implicit user: User): Future[Either[F, Unit]] = {
      val result = for {
        _ <- EitherT(super.preDelete(entity))
        _ <- EitherT(assetSharingService.deleteSharesForAsset(entity.id, assetType).map(_ => ().asRight[F]))
      } yield ()

      result.value
    }

  }

  trait WithNestedUsageTracking[T <: Asset[_], F] extends AssetService[T, F] with NestedUsageChecker { self =>

    val inUseError: F

    override protected[services] def ensureCanDelete(asset: WithId[T], user: User): Future[Either[F, Unit]] = {
      val result = for {
        _ <- EitherT(super.ensureCanDelete(asset, user))
        _ <- checkNestedUsage(AssetReference(asset.id, assetType), user).leftMap(_ => inUseError)
      } yield ()

      result.value
    }

    protected[services] def ensureCanUpdateContent(asset: WithId[T], user: User): Future[Either[F, Unit]] = {
      checkNestedUsage(AssetReference(asset.id, assetType), user).leftMap(_ => inUseError).value
    }

    def checkNestedUsage(assetReference: AssetReference, user: User): NestedUsageResult =
      assetIsFree

  }

  trait WithCreate[T <: Asset[_], F, E] extends AssetService[T, F] {

    protected val createErrors: AssetCreateErrors[E]

    protected def validateAndGetAssetCreateParams(
      name: Option[String],
      inLibrary: Option[Boolean]
    )(implicit user: User): EitherT[Future, E, AssetCreateParams] =
      user match {
        case _: RegularUser =>
          for {
            specifiedName <- EitherT.fromOption[Future](name, createErrors.nameNotSpecifiedError)
            _ <- EitherT(validateAssetName(
              specifiedName,
              None,
              createErrors.emptyNameError,
              createErrors.nameAlreadyExistsError(specifiedName)
            ))
          } yield AssetCreateParams(specifiedName, inLibrary = true)
        case experimentExecutor: ExperimentExecutor =>
          for {
            _ <- name.validate { name: String =>
              EitherT.cond[Future](name.nonEmpty, (), createErrors.emptyNameError)
            }
            generatedName <- EitherT.right[E](
              UniqueNameGenerator.generateUniqueName(
                name.getOrElse(s"Experiment #${ experimentExecutor.experimentId } result"),
                " "
              )(name => dao.count(sameNameFilter(name)).map(_ == 0))
            )
          } yield AssetCreateParams(generatedName, inLibrary = inLibrary.getOrElse(false))
      }

  }

  sealed trait AssetProcessGetError
  object AssetProcessGetError {
    case object AssetNotFound extends AssetProcessGetError
    case object ProcessNotFound extends AssetProcessGetError
    case object AccessDenied extends AssetProcessGetError
  }

  trait AssetCreateErrors[E] {
    val nameNotSpecifiedError: E
    val emptyNameError: E

    def nameAlreadyExistsError(name: String): E
  }

  case class AssetCreateParams(name: String, inLibrary: Boolean)

}
