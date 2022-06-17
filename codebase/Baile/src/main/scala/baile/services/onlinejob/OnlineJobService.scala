package baile.services.onlinejob

import java.time.Instant
import java.util.UUID

import akka.event.LoggingAdapter
import baile.dao.onlinejob.OnlineJobDao
import baile.daocommons.WithId
import baile.daocommons.filters.{ Filter, TrueFilter }
import baile.daocommons.sorting.Field
import baile.domain.asset.AssetType
import baile.domain.onlinejob.{ OnlineJob, OnlineJobOptions, OnlineJobStatus, OnlinePredictionOptions }
import baile.domain.usermanagement.User
import baile.services.asset.AssetService
import baile.services.asset.AssetService.{ WithNestedUsageTracking, WithOwnershipTransfer, WithSharedAccess }
import baile.services.asset.AssetService.{
  AssetCreateErrors,
  AssetCreateParams,
  WithCreate,
  WithNestedUsageTracking,
  WithSharedAccess
}
import baile.services.asset.sharing.AssetSharingService
import baile.services.common.EntityService.WithSortByField
import baile.services.common.S3BucketService.BucketDereferenceError
import baile.services.onlinejob.OnlineJobService.OnlineJobServiceError
import baile.services.onlinejob.OnlinePredictionConfigurator.OnlinePredictionConfiguratorError
import baile.services.onlinejob.exceptions.UnsupportedOptionsException
import baile.services.project.ProjectService
import cats.data.EitherT
import cats.implicits._

import scala.concurrent.{ ExecutionContext, Future }

class OnlineJobService(
  protected val dao: OnlineJobDao,
  protected val onlinePredictionConfigurator: OnlinePredictionConfigurator,
  protected val assetSharingService: AssetSharingService,
  protected val projectService: ProjectService
)(
  implicit val ec: ExecutionContext,
  val logger: LoggingAdapter
) extends AssetService[OnlineJob, OnlineJobServiceError]
  with WithSortByField[OnlineJob, OnlineJobServiceError]
  with WithSharedAccess[OnlineJob, OnlineJobServiceError]
  with WithNestedUsageTracking[OnlineJob, OnlineJobServiceError]
  with WithOwnershipTransfer[OnlineJob]
  with WithCreate[OnlineJob, OnlineJobServiceError, OnlineJobServiceError] {

  import OnlineJobServiceError._

  override val assetType: AssetType = AssetType.OnlineJob
  override val forbiddenError: OnlineJobServiceError = AccessDenied
  override val notFoundError: OnlineJobServiceError = OnlineJobNotFound
  override val inUseError: OnlineJobServiceError = OnlineJobInUse
  override val sortingFieldNotFoundError: OnlineJobServiceError = SortingFieldUnknown

  override protected val createErrors: AssetCreateErrors[OnlineJobServiceError] = OnlineJobServiceError
  override protected val findField: String => Option[Field] = Map(
    "enabled" -> OnlineJobDao.Enabled,
    "name" -> OnlineJobDao.Name,
    "created" -> OnlineJobDao.Created,
    "updated" -> OnlineJobDao.Updated
  ).get

  override def updateOwnerId(onlineJob: OnlineJob, ownerId: UUID): OnlineJob = onlineJob.copy(ownerId = ownerId)

  def create(
    name: Option[String],
    enabled: Boolean,
    options: OnlineJobCreateOptions,
    description: Option[String]
  )(implicit user: User): Future[Either[OnlineJobServiceError, WithId[OnlineJob]]] = {

    // TODO remove this method when different jobs will be implemented
    def validateSingleJob(): Future[Either[OnlineJobServiceError, Unit]] = dao.count(TrueFilter).map { count =>
      if (count > 0) OnlineJobAlreadyExists.asLeft else ().asRight
    }

    def configureJob(): Future[Either[OnlineJobServiceError, OnlinePredictionOptions]] = options match {
      case onlinePredictionCreateOptions: OnlinePredictionCreateOptions =>
        onlinePredictionConfigurator.configure(onlinePredictionCreateOptions)
          .map(_.leftMap(translateConfiguratorErrorToOnlineJobServiceError))
      case _ => Future.failed(UnsupportedOptionsException(options))
    }

    def saveJob(createParams: AssetCreateParams, configuredOptions: OnlineJobOptions): Future[WithId[OnlineJob]] = {
      val dateTime = Instant.now()
      val onlineJob = OnlineJob(
        ownerId = user.id,
        name = createParams.name,
        status = OnlineJobStatus.Running,
        options = configuredOptions,
        enabled = enabled,
        created = dateTime,
        updated = dateTime,
        description = description
      )
      dao.create(onlineJob).map(id => WithId[OnlineJob](onlineJob, id))
    }

    val result = for {
      createParams <- validateAndGetAssetCreateParams(name, None)
      _ <- EitherT(validateSingleJob())
      configuredOptions <- EitherT(configureJob())
      onlineJob <- EitherT.right[OnlineJobServiceError](saveJob(createParams, configuredOptions))
    } yield onlineJob

    result.value
  }

  private def translateConfiguratorErrorToOnlineJobServiceError(
    error: OnlinePredictionConfiguratorError
  ): OnlineJobServiceError = {
    error match {
      case OnlinePredictionConfiguratorError.AccessDenied => AccessDenied
      case OnlinePredictionConfiguratorError.ModelNotFound => ModelNotFound
      case OnlinePredictionConfiguratorError.ModelNotActive => ModelNotActive
      case OnlinePredictionConfiguratorError.InvalidModelType => InvalidModelType
      case OnlinePredictionConfiguratorError.BucketError(error) => BucketError(error)
    }
  }

  def update(
    id: String,
    newName: Option[String],
    newDescription: Option[String],
    enabled: Option[Boolean]
  )(implicit user: User): Future[Either[OnlineJobServiceError, WithId[OnlineJob]]] =
    update(id, onlineJob => onlineJob.copy(
      name = newName.getOrElse(onlineJob.name),
      description = newDescription orElse onlineJob.description,
      enabled = enabled.getOrElse(onlineJob.enabled),
      updated = Instant.now()
    ))

  override protected def prepareCanReadFilter(user: User): Future[Filter] =
    Future.successful(TrueFilter)

  override protected def ensureOwnership(
    asset: WithId[OnlineJob],
    user: User
  ): Future[Either[OnlineJobServiceError, Unit]] = Future.successful {
    ().asRight
  }

}

object OnlineJobService {

  sealed trait OnlineJobServiceError

  object OnlineJobServiceError extends AssetCreateErrors[OnlineJobServiceError] {

    case object OnlineJobNotFound extends OnlineJobServiceError
    case object AccessDenied extends OnlineJobServiceError
    case object SortingFieldUnknown extends OnlineJobServiceError
    case object OnlineJobAlreadyExists extends OnlineJobServiceError
    case object ModelNotFound extends OnlineJobServiceError
    case object ModelNotActive extends OnlineJobServiceError
    case object InvalidModelType extends OnlineJobServiceError
    case class BucketError(error: BucketDereferenceError) extends OnlineJobServiceError
    case object OnlineJobInUse extends OnlineJobServiceError
    case object NameNotSpecified extends OnlineJobServiceError
    case object EmptyName extends OnlineJobServiceError
    case object NameIsTaken extends OnlineJobServiceError

    override val nameNotSpecifiedError: OnlineJobServiceError = NameNotSpecified
    override val emptyNameError: OnlineJobServiceError = EmptyName

    override def nameAlreadyExistsError(name: String): OnlineJobServiceError = NameIsTaken
  }

}
