package baile.services.pipeline

import baile.dao.pipeline.{ PipelineDao, PipelineOperatorDao }
import baile.daocommons.WithId
import baile.domain.asset.sharing.SharedResource
import baile.domain.asset.{ AssetReference, AssetType }
import baile.domain.pipeline.{ AssetParameterTypeInfo, PipelineOperator, PipelineStep, PipelineStepInfo }
import baile.domain.pipeline.PipelineParams.{ StringParam, StringParams }
import baile.services.asset.sharing.SharedAccessChecker
import cats.data.{ EitherT, OptionT }
import cats.implicits._

import scala.concurrent.Future

trait PipelineSharedAccess extends SharedAccessChecker {

  protected val pipelineDao: PipelineDao
  protected val pipelineOperatorDao: PipelineOperatorDao

  abstract override def checkSharedAccess(
    assetReference: AssetReference,
    sharedResource: SharedResource
  ): EitherT[Future, Unit, Unit] =
    super.checkSharedAccess(assetReference, sharedResource) orElse {
      sharedResource.assetType match {
        case AssetType.Pipeline =>
          for {
            pipeline <- EitherT.fromOptionF(pipelineDao.get(sharedResource.assetId), ())
            pipelineOperators <- EitherT(getPipelineOperators(pipeline.entity.steps.map(_.step)))
            result <- accessGrantedIf(
              pipeline.entity.steps.exists {
                case PipelineStepInfo(step, _) =>
                  pipelineOperators(step.operatorId).entity.params.exists { param =>
                    param.typeInfo match {
                      case AssetParameterTypeInfo(assetType) =>
                        assetType == assetReference.`type` && step.params.exists {
                          case (param.name, StringParam(id)) => id == assetReference.id
                          case (param.name, StringParams(ids)) => ids.contains(assetReference.id)
                          case _ => false
                        }
                      case _ => false
                    }
                  }
              }
            )
          } yield result
        case _ => accessDenied
      }
    }

  private def getPipelineOperators(
    steps: Seq[PipelineStep]
  ): Future[Either[Unit, Map[String, WithId[PipelineOperator]]]] = {
    type EitherTOr[R] = EitherT[Future, Unit, R]

    val result = steps.toList.foldM[EitherTOr, Map[String, WithId[PipelineOperator]]](Map.empty) { (soFar, step) =>
      OptionT
        .fromOption[Future](soFar.get(step.operatorId))
        .orElseF(pipelineOperatorDao.get(step.operatorId))
        .toRight(())
        .map { operator =>
          soFar + (operator.id -> operator)
        }
    }

    result.value
  }
}
