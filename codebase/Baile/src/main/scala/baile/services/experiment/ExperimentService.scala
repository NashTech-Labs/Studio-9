package baile.services.experiment

import java.time.Instant
import java.util.UUID

import akka.event.LoggingAdapter
import baile.dao.cv.model.CVModelDao
import baile.dao.experiment.ExperimentDao
import baile.dao.tabular.model.TabularModelDao
import baile.daocommons.sorting.Field
import baile.daocommons.{ EntityDao, WithId }
import baile.domain.asset.{ Asset, AssetReference, AssetType }
import baile.domain.experiment.pipeline.ExperimentPipeline
import baile.domain.experiment.result.ExperimentResult
import baile.domain.experiment.{ Experiment, ExperimentStatus }
import baile.domain.usermanagement.User
import baile.services.asset.AssetService
import baile.services.asset.AssetService.{ WithOwnershipTransfer, WithProcess, WithSharedAccess }
import baile.services.asset.AssetService._
import baile.services.asset.sharing.AssetSharingService
import baile.services.common.EntityService.WithSortByField
import baile.services.experiment.ExperimentService.ExperimentServiceError
import baile.services.experiment.ExperimentService.ExperimentServiceError.ExperimentInUse
import baile.services.process.ProcessService
import baile.services.project.ProjectService
import baile.utils.validation.Option._
import cats.data.EitherT
import cats.implicits._

import scala.concurrent.{ ExecutionContext, Future }

abstract class ExperimentService(
  protected val dao: ExperimentDao,
  protected val projectService: ProjectService,
  protected val processService: ProcessService,
  protected val assetSharingService: AssetSharingService,
  protected val experimentDelegator: ExperimentDelegator
)(
  implicit val ec: ExecutionContext,
  val logger: LoggingAdapter
) extends AssetService[Experiment, ExperimentServiceError]
  with WithSortByField[Experiment, ExperimentServiceError]
  with WithProcess[Experiment, ExperimentServiceError]
  with WithSharedAccess[Experiment, ExperimentServiceError]
  with WithOwnershipTransfer[Experiment]
  with WithCreate[Experiment, ExperimentServiceError, ExperimentServiceError]
  with WithNestedUsageTracking[Experiment, ExperimentServiceError] {

  override val assetType: AssetType = AssetType.Experiment
  override val forbiddenError: ExperimentServiceError = ExperimentServiceError.AccessDenied
  override val sortingFieldNotFoundError: ExperimentServiceError = ExperimentServiceError.SortingFieldUnknown
  override val notFoundError: ExperimentServiceError = ExperimentServiceError.ExperimentNotFound
  override protected val createErrors: AssetCreateErrors[ExperimentServiceError] = ExperimentServiceError
  override val inUseError: ExperimentServiceError = ExperimentInUse

  override protected val findField: String => Option[Field] = Map(
    "name" -> ExperimentDao.Name,
    "created" -> ExperimentDao.Created,
    "updated" -> ExperimentDao.Updated
  ).get

  override def updateOwnerId(experiment: Experiment, ownerId: UUID): Experiment = experiment.copy(ownerId = ownerId)

  def create(
    name: Option[String],
    description: Option[String],
    pipeline: ExperimentPipeline
  )(implicit user: User): Future[Either[ExperimentServiceError, WithId[Experiment]]] = {

    def createExperiment(
      createParams: AssetCreateParams,
      updatedPipeline: ExperimentPipeline
    ): Future[WithId[Experiment]] = {
      val now = Instant.now()

      dao.create(_ =>
        Experiment(
          name = createParams.name,
          ownerId = user.id,
          description = description,
          status = ExperimentStatus.Running,
          pipeline = updatedPipeline,
          result = None,
          created = now,
          updated = now
        )
      )
    }

    val result = for {
      createParams <- validateAndGetAssetCreateParams(name, None)
      experimentCreatedResult <- EitherT(experimentDelegator.validateAndCreatePipeline(
        pipeline,
        createParams.name,
        description
      )).leftMap[ExperimentServiceError](ExperimentServiceError.ExperimentError)
      experiment <- EitherT.right[ExperimentServiceError](
        createExperiment(createParams, experimentCreatedResult.pipeline)
      )
      _ <- EitherT.right[ExperimentServiceError](experimentCreatedResult.handler(experiment.id))
    } yield experiment

    result.value
  }

  def update(
    id: String,
    newName: Option[String],
    newDescription: Option[String]
  )(implicit user: User): Future[Either[ExperimentServiceError, WithId[Experiment]]] = {

    update(
      id,
      _ => newName.validate(name => validateAssetName(
        name,
        Option(id),
        ExperimentServiceError.EmptyExperimentName,
        ExperimentServiceError.NameIsTaken
      )),
      model => model.copy(
        name = newName.getOrElse(model.name),
        description = newDescription orElse model.description
      )
    )
  }

  override def preDelete(
    entity: WithId[Experiment]
  )(implicit user: User): Future[Either[ExperimentServiceError, Unit]] = {

    def cleanUpAsset(experimentResult: ExperimentResult): Future[Unit] = {
      Future.sequence(experimentResult.getAssetReferences.toList.map { assetReference =>
        deleteAsset(assetReference, user)
      }).map(_ => ())
    }

    val result = for {
      _ <- EitherT(super.preDelete(entity))
      _ <- EitherT.right[ExperimentServiceError](
        entity.entity.result
          .map(cleanUpAsset)
          .getOrElse(Future.unit)
      )
    } yield ()

    result.value
  }

  protected def daoByAssetType(assetType: AssetType): EntityDao[_ <: Asset[_]]

  private def deleteAsset(assetReference: AssetReference, user: User): Future[Unit] = {
    val dao = daoByAssetType(assetReference.`type`)
    checkNestedUsageAndHandleAssetIfRequired(assetReference, dao, user)
  }

  private def checkNestedUsageAndHandleAssetIfRequired(
    assetReference: AssetReference,
    assetDao: EntityDao[_ <: Asset[_]],
    user: User
  ): Future[Unit] = {

    def handleAssetIfRequired(usageResult: Either[Unit, Unit]): Future[Unit] = {
      usageResult match {
        case Left(_) => assetDao match {
          case dao: CVModelDao => dao.update(assetReference.id, _.copy(experimentId = None)).map(_ => ())
          case dao: TabularModelDao => dao.update(assetReference.id, _.copy(experimentId = None)).map(_ => ())
          case _ => Future.unit
        }
        case Right(_) => for {
          asset <- loadAssetMandatory(assetReference.id, assetDao)
          _ <- deleteAssetIfNotInLibrary(asset, assetDao)
        } yield ()
      }
    }

    for {
      checkNestedUsageResult <- checkNestedUsage(assetReference, user).value
      result <- handleAssetIfRequired(checkNestedUsageResult)
    } yield result
  }

  private def deleteAssetIfNotInLibrary(
    asset: WithId[Asset[_]],
    assetDao: EntityDao[_ <: Asset[_]]
  ): Future[Unit] =
    if (asset.entity.inLibrary) {
      assetDao match {
        case dao: CVModelDao  => dao.update(asset.id, _.copy(experimentId = None)).map(_ => ())
        case dao: TabularModelDao => dao.update(asset.id, _.copy(experimentId = None)).map(_ => ())
        case _ => Future.unit
      }
    } else {
      assetDao.delete(asset.id).map(_ => ())
    }

  private def loadAssetMandatory(
    id: String,
    assetDao: EntityDao[_ <: Asset[_]]
  ): Future[WithId[Asset[_]]] =
    assetDao.get(id).map(_.getOrElse(throw new RuntimeException(
      s"Unexpectedly not found asset $id in storage ${ assetDao.getClass.getName }"
    )))

}

object ExperimentService {

  sealed trait ExperimentServiceError

  object ExperimentServiceError extends AssetCreateErrors[ExperimentServiceError] {

    case class ExperimentError(e: PipelineHandler.CreateError) extends ExperimentServiceError
    case object ExperimentNotFound extends ExperimentServiceError
    case object AccessDenied extends ExperimentServiceError
    case object SortingFieldUnknown extends ExperimentServiceError
    case object NameNotSpecified extends ExperimentServiceError
    case object EmptyExperimentName extends ExperimentServiceError
    case object NameIsTaken extends ExperimentServiceError
    case object ExperimentInUse extends ExperimentServiceError

    override val nameNotSpecifiedError: ExperimentServiceError = NameNotSpecified
    override val emptyNameError: ExperimentServiceError = EmptyExperimentName

    override def nameAlreadyExistsError(name: String): ExperimentServiceError = NameIsTaken
  }

}
