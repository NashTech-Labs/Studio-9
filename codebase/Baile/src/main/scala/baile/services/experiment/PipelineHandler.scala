package baile.services.experiment

import baile.daocommons.{ EntityDao, WithId }
import baile.domain.asset.Asset
import baile.domain.process.Process
import baile.domain.experiment.pipeline.ExperimentPipeline
import baile.domain.experiment.result.ExperimentResult
import baile.domain.usermanagement.User
import baile.services.experiment.PipelineHandler.{ CreateError, PipelineCreatedResult }
import cats.data.EitherT
import cats.implicits._

import scala.concurrent.{ ExecutionContext, Future }

trait PipelineHandler[P <: ExperimentPipeline, S, R <: ExperimentResult, F <: CreateError] {

  implicit val ec: ExecutionContext

  final def validateAndCreatePipeline(
    pipeline: P,
    experimentName: String,
    experimentDescription: Option[String]
  )(implicit user: User): Future[Either[F, PipelineCreatedResult[P]]] = {
    val result = for {
      params <- EitherT(validatePipelineAndLoadParams(pipeline))
      experimentCreatedHandler <- EitherT.right[F](createPipeline(
        params,
        pipeline,
        experimentName,
        experimentDescription
      ))
    } yield experimentCreatedHandler

    result.value
  }

  protected def validatePipelineAndLoadParams(pipeline: P)(implicit user: User): Future[Either[F, S]]

  protected def createPipeline(
    params: S,
    pipeline: P,
    experimentName: String,
    experimentDescription: Option[String]
  )(implicit user: User): Future[PipelineCreatedResult[P]]

  protected final def deleteIfNotInLibrary[A <: Asset[_]](
    asset: WithId[A],
    assetDao: EntityDao[A]
  )(implicit user: User): Future[Unit] =
    if (asset.entity.inLibrary) Future.unit
    else assetDao.delete(asset.id).map(_ => ())

}

object PipelineHandler {

  trait CreateError

  type ExperimentId = String
  type ExperimentCreatedHandler = ExperimentId => Future[WithId[Process]]
  case class PipelineCreatedResult[P <: ExperimentPipeline](
    handler: ExperimentCreatedHandler,
    pipeline: P
  )

}
