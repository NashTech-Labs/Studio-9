package baile.services.pipeline

import java.time.Instant
import java.util.UUID

import akka.event.LoggingAdapter
import baile.dao.pipeline.PipelineDao
import baile.daocommons.WithId
import baile.daocommons.sorting.Field
import baile.domain.asset.AssetType
import baile.domain.pipeline._
import baile.domain.usermanagement.User
import baile.services.asset.AssetService
import baile.services.asset.AssetService.{ WithOwnershipTransfer, WithSharedAccess }
import baile.services.asset.AssetService.{
  AssetCreateErrors,
  AssetCreateParams,
  WithCreate,
  WithNestedUsageTracking,
  WithSharedAccess
}
import baile.services.asset.sharing.AssetSharingService
import baile.services.common.EntityService.WithSortByField
import baile.services.pipeline.PipelineOperatorService.PipelineOperatorServiceError
import baile.services.pipeline.PipelineService.PipelineServiceError
import baile.services.project.ProjectService
import baile.utils.validation.Option._
import cats.data.EitherT
import cats.implicits._

import scala.concurrent.{ ExecutionContext, Future }

class PipelineService(
  protected val dao: PipelineDao,
  pipelineOperatorService: PipelineOperatorService,
  protected val projectService: ProjectService,
  protected val assetSharingService: AssetSharingService
)(
  implicit val ec: ExecutionContext,
  val logger: LoggingAdapter
) extends AssetService[Pipeline, PipelineServiceError]
  with WithSortByField[Pipeline, PipelineServiceError]
  with WithSharedAccess[Pipeline, PipelineServiceError]
  with WithOwnershipTransfer[Pipeline]
  with WithNestedUsageTracking[Pipeline, PipelineServiceError]
  with WithCreate[Pipeline, PipelineServiceError, PipelineServiceError] {

  import baile.services.pipeline.PipelineService.PipelineServiceError._

  override val assetType: AssetType = AssetType.Pipeline
  override val forbiddenError: PipelineServiceError = AccessDenied
  override val sortingFieldNotFoundError: PipelineServiceError = SortingFieldUnknown
  override val notFoundError: PipelineServiceError = PipelineNotFound
  override val inUseError: PipelineServiceError = PipelineInUse

  override protected val createErrors: AssetCreateErrors[PipelineServiceError] = PipelineServiceError
  override protected val findField: String => Option[Field] = Map(
    "name" -> PipelineDao.Name,
    "created" -> PipelineDao.Created,
    "updated" -> PipelineDao.Updated
  ).get

  override def updateOwnerId(pipeline: Pipeline, ownerId: UUID): Pipeline = pipeline.copy(ownerId = ownerId)

  def create(
    name: Option[String],
    description: Option[String],
    inLibrary: Option[Boolean],
    steps: Seq[PipelineStepInfo]
  )(implicit user: User): Future[Either[PipelineServiceError, WithId[Pipeline]]] = {

    def savePipeline(createParams: AssetCreateParams): Future[WithId[Pipeline]] = {
      val now = Instant.now()
      val pipeline = Pipeline(
        name = createParams.name,
        description = description,
        ownerId = user.id,
        status = PipelineStatus.Idle,
        created = now,
        updated = now,
        steps = steps,
        inLibrary = createParams.inLibrary
      )
      dao.create(_ => pipeline)
    }

    val result = for {
      createParams <- validateAndGetAssetCreateParams(name, inLibrary)
      pipelineOperators <- EitherT(getPipelineOperators(steps.map(_.step)))
      _ <- EitherT.fromEither[Future](
        PipelineValidator.validatePipelineStepInfos(steps, pipelineOperators)
      ).leftMap(PipelineStepValidationError)
      pipeline <- EitherT.right[PipelineServiceError](savePipeline(createParams))
    } yield pipeline

    result.value
  }

  def update(
    id: String,
    newName: Option[String],
    newDescription: Option[String],
    steps: Option[Seq[PipelineStepInfo]]
  )(implicit user: User): Future[Either[PipelineServiceError, WithId[Pipeline]]] = {
    val result = for {
      pipelineOperators <- EitherT(getPipelineOperators(steps.getOrElse(Nil).map(_.step)))
      _ <- EitherT.fromEither[Future](
        steps.validate(PipelineValidator.validatePipelineStepInfos(_, pipelineOperators))
      ).leftMap(PipelineStepValidationError)
      pipeline <- EitherT(update(
        id,
        _ => newName.validate(name => validateAssetName[PipelineServiceError](
          name,
          Option(id),
          EmptyPipelineName,
          NameIsTaken
        )),
        pipeline => pipeline.copy(
          name = newName.getOrElse(pipeline.name),
          updated = Instant.now(),
          description = newDescription orElse pipeline.description,
          steps = steps getOrElse pipeline.steps
        )
      ))
    } yield pipeline

    result.value
  }

  private[pipeline] def getPipelineOperators(
    steps: Seq[PipelineStep]
  )(implicit user: User): Future[Either[PipelineServiceError, Map[String, WithId[PipelineOperator]]]] = {
    type EitherTOr[R] = EitherT[Future, PipelineServiceError, R]
    val result = steps.toList.foldM[EitherTOr, Map[String, WithId[PipelineOperator]]](Map.empty) { (soFar, step) =>
      val pipelineOperator = soFar.get(step.operatorId) match {
        case Some(operator) => EitherT.rightT[Future, PipelineServiceError](operator)
        case None => EitherT(pipelineOperatorService.getPipelineOperator(step.operatorId))
          .bimap[PipelineServiceError, WithId[PipelineOperator]](PipelineOperatorError, _.operator)
      }
      pipelineOperator.map(operator => soFar + (operator.id -> operator))
    }

    result.value
  }

}

object PipelineService {

  sealed trait PipelineServiceError

  object PipelineServiceError extends AssetCreateErrors[PipelineServiceError] {

    case object AssetNotFound extends PipelineServiceError

    case object AccessDenied extends PipelineServiceError

    case object PipelineNotFound extends PipelineServiceError

    case object PipelineInUse extends PipelineServiceError

    case object SortingFieldUnknown extends PipelineServiceError

    case object EmptyPipelineName extends PipelineServiceError

    case object NameIsTaken extends PipelineServiceError

    case object NameNotSpecified extends PipelineServiceError

    case class PipelineStepValidationError(error: PipelineValidationError) extends PipelineServiceError

    case class PipelineOperatorError(error: PipelineOperatorServiceError) extends PipelineServiceError

    override val nameNotSpecifiedError: PipelineServiceError = NameNotSpecified
    override val emptyNameError: PipelineServiceError = EmptyPipelineName

    override def nameAlreadyExistsError(name: String): PipelineServiceError = NameIsTaken
  }

}
