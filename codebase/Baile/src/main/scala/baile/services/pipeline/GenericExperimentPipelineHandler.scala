package baile.services.pipeline

import java.util.UUID

import baile.dao.pipeline.PipelineOperatorDao
import baile.daocommons.WithId
import baile.domain.asset.{ AssetReference, AssetType }
import baile.domain.pipeline.PipelineParams.{ PipelineParams, PipelineParam => DomainPipelineParam, _ }
import baile.domain.pipeline._
import baile.domain.pipeline.pipeline.GenericExperimentPipeline
import baile.domain.pipeline.result.GenericExperimentResult
import baile.domain.usermanagement.User
import baile.services.cortex.job.CortexJobService
import baile.services.dcproject.DCProjectPackageService
import baile.services.experiment.PipelineHandler
import baile.services.experiment.PipelineHandler.{ CreateError, PipelineCreatedResult }
import baile.services.pipeline.GenericExperimentPipelineHandler.GenericPipelineCreateError._
import baile.services.pipeline.GenericExperimentPipelineHandler.{
  GenericExperimentPipelineParams,
  GenericPipelineCreateError,
  UntappedStepInputs
}
import baile.services.pipeline.PipelineService.PipelineServiceError
import baile.services.process.ProcessService
import cats.data.EitherT
import cats.implicits._
import cortex.api.job.common.ClassReference
import cortex.api.job.pipeline.{ PipelineOutputReference => CortexPipelineOutputReference, _ }

import scala.concurrent.{ ExecutionContext, Future }

class GenericExperimentPipelineHandler(
  cortexJobService: CortexJobService,
  processService: ProcessService,
  pipelineOperatorDao: PipelineOperatorDao,
  packageService: DCProjectPackageService,
  pipelineService: PipelineService
)(implicit val ec: ExecutionContext) extends PipelineHandler[
  GenericExperimentPipeline,
  GenericExperimentPipelineParams,
  GenericExperimentResult,
  GenericPipelineCreateError
] {

  type ErrorOr[R] = Either[GenericPipelineCreateError, R]

  type FutureErrorOr[R] = Future[ErrorOr[R]]

  // scalastyle:off method.length
  override protected def validatePipelineAndLoadParams(
    pipeline: GenericExperimentPipeline
  )(implicit user: User): FutureErrorOr[GenericExperimentPipelineParams] = {

    def validateRequiredParamsAreProvided(
      steps: Seq[PipelineStep],
      pipelineOperators: Map[String, WithId[PipelineOperator]]
    ): Either[GenericPipelineCreateError, Unit] = {

      def validateRequiredParamsAreProvided(
        operator: WithId[PipelineOperator],
        stepParams: PipelineParams
      ): Either[GenericPipelineCreateError, Unit] = {

        def validateParameterIsProvidedInStep(operatorParamName: String): Either[GenericPipelineCreateError, Unit] = {
          if (stepParams.isDefinedAt(operatorParamName)) ().asRight
          else ParameterNotProvided(operator.id, operator.entity.name, operatorParamName).asLeft
        }

        operator.entity.params.toList.foldM[ErrorOr, Unit](
          ().asRight[GenericPipelineCreateError]
        ) {
          case (_, operatorParam) =>
            if (PipelineValidator.operatorParameterHasDefault(operatorParam.typeInfo) ||
              PipelineValidator.validateConditions(operatorParam, stepParams).isLeft) {
              ().asRight
            }
            else validateParameterIsProvidedInStep(operatorParam.name)
        }
      }

      steps.toList.foldM[ErrorOr, Unit](
        ().asRight[GenericPipelineCreateError]
      ) { (_, step) =>
        validateRequiredParamsAreProvided(
          pipelineOperators(step.operatorId),
          step.params
        )
      }
    }

    def getAssetReferences(
      operators: Map[String, WithId[PipelineOperator]]
    ): Either[GenericPipelineCreateError, List[AssetReference]] =
      pipeline.steps.toList.foldM[ErrorOr, List[AssetReference]](List.empty) { case (soFar, step) =>
        val operator = operators(step.operatorId).entity
        operator
          .params
          .toList
          .collect {
            case OperatorParameter(paramName, _, _, typeInfo: AssetParameterTypeInfo, _) =>
              step.params.get(paramName).map {
                case StringParam(assetId) => AssetReference(assetId, typeInfo.assetType).asRight
                case _ => InvalidAssetReferenceValueType(step.operatorId, operator.name, paramName).asLeft
              }
          }
          .collect { case Some(x) => x }
          .sequence[ErrorOr, AssetReference]
          .map(_ ++ soFar)
      }

    def checkAllInputsAreConnected(
      pipelineOperators: Map[String, WithId[PipelineOperator]]
    ): Either[GenericPipelineCreateError, Unit] = {
      val untappedStepInputs = pipeline.steps.flatMap { step =>
        val operator = pipelineOperators(step.operatorId)
        val requiredInputs = operator.entity.inputs.filter(_.required).map(_.name).toSet
        val stepInputs = step.inputs.keySet
        if (requiredInputs.subsetOf(stepInputs)) None
        else Some(UntappedStepInputs(step.id, operator.entity.name, requiredInputs.diff(stepInputs)))
      }

      Either.cond(
        untappedStepInputs.isEmpty,
        (),
        NotAllInputsAreConnected(untappedStepInputs)
      )
    }

    val result = for {
      pipelineOperators <- EitherT(pipelineService.getPipelineOperators(pipeline.steps))
        .leftMap(GenericPipelineServiceError)
      _ <- EitherT.fromEither[Future](
        PipelineValidator.validatePipelineSteps(pipeline.steps, pipelineOperators)
      ).leftMap(GenericPipelineStepValidationError)
      _ <- EitherT.fromEither[Future](checkAllInputsAreConnected(pipelineOperators))
      _ <- EitherT.fromEither[Future](validateRequiredParamsAreProvided(pipeline.steps, pipelineOperators))
      assetReferences <- EitherT.fromEither[Future](getAssetReferences(pipelineOperators))
    } yield GenericExperimentPipelineParams(pipelineOperators, assetReferences)

    result.value
  }
  // scalastyle:on method.length

  override protected def createPipeline(
    params: GenericExperimentPipelineParams,
    pipeline: GenericExperimentPipeline,
    experimentName: String,
    experimentDescription: Option[String]
  )(implicit user: User): Future[PipelineCreatedResult[GenericExperimentPipeline]] = {

    def generatePipelineStepRequest(pipelineStep: PipelineStep): Future[PipelineStepRequest] = {
      for {
        pipelineOperator <- Future.successful(params.pipelineOperators(pipelineStep.operatorId))
        dcPackage <- packageService.loadPackageMandatory(pipelineOperator.entity.packageId)
      } yield {
        PipelineStepRequest(
          stepId = pipelineStep.id,
          operator = Some(ClassReference(
            dcPackage.entity.location,
            pipelineOperator.entity.className,
            pipelineOperator.entity.moduleName
          )),
          inputs = pipelineStep.inputs.mapValues(outputReference => CortexPipelineOutputReference(
            stepId = outputReference.stepId,
            outputIndex = outputReference.outputIndex
          )),
          params = pipelineStep.params map { case (paramName, param) =>
            val operatorParameter = pipelineOperator.entity.params.find(_.name == paramName).get
            (paramName, buildParameterValue(param, operatorParameter.typeInfo))
          }
        )
      }

    }

    for {
      pipelineStepRequest <- Future.sequence(pipeline.steps.map(generatePipelineStepRequest))
      authToken = UUID.randomUUID().toString
      jobMessage = PipelineRunRequest(pipelineStepRequest, baileAuthToken = authToken)
      experimentCreatedHandler = { experimentId: String =>
        for {
          jobId <- cortexJobService.submitJob(jobMessage, user.id)
          process <- processService.startProcess(
            jobId = jobId,
            targetId = experimentId,
            targetType = AssetType.Experiment,
            handlerClass = classOf[PipelineJobResultHandler],
            meta = PipelineJobResultHandler.Meta(experimentId),
            userId = user.id,
            authToken = Some(authToken)
          )
        } yield process
      }
    } yield {
      PipelineCreatedResult(
        handler = experimentCreatedHandler,
        pipeline = GenericExperimentPipeline(
          pipeline.steps,
          params.assetReferences
        )
      )
    }
  }

  private def buildParameterValue(param: DomainPipelineParam, typeInfo: ParameterTypeInfo): PipelineParam = {
    val parameterValue = param match {
      case StringParam(value) => PipelineParam.Param.StringParam(value)
      case IntParam(value) => PipelineParam.Param.IntParam(value)
      case FloatParam(value) => PipelineParam.Param.FloatParam(value)
      case BooleanParam(value) => PipelineParam.Param.BooleanParam(value)
      case StringParams(values) => PipelineParam.Param.StringParams(StringSequenceParams(values))
      case IntParams(values) => PipelineParam.Param.IntParams(IntSequenceParams(values))
      case FloatParams(values) => PipelineParam.Param.FloatParams(FloatSequenceParams(values))
      case BooleanParams(values) => PipelineParam.Param.BooleanParams(BooleanSequenceParams(values))
      case EmptySeqParam =>
        typeInfo match {
          case _: BooleanParameterTypeInfo => PipelineParam.Param.BooleanParams(BooleanSequenceParams(List.empty))
          case _: FloatParameterTypeInfo => PipelineParam.Param.FloatParams(FloatSequenceParams(List.empty))
          case _: IntParameterTypeInfo => PipelineParam.Param.IntParams(IntSequenceParams(List.empty))
          case _: StringParameterTypeInfo => PipelineParam.Param.StringParams(StringSequenceParams(List.empty))
          case _: AssetParameterTypeInfo => PipelineParam.Param.StringParams(StringSequenceParams(List.empty))
        }
    }

    PipelineParam(parameterValue)
  }

}

object GenericExperimentPipelineHandler {

  case class GenericExperimentPipelineParams(
    pipelineOperators: Map[String, WithId[PipelineOperator]],
    assetReferences: Seq[AssetReference]
  )

  case class UntappedStepInputs(
    stepId: String,
    operatorName: String,
    inputNames: Set[String]
  )

  sealed trait GenericPipelineCreateError extends CreateError

  object GenericPipelineCreateError {

    case class GenericPipelineStepValidationError(error: PipelineValidationError) extends GenericPipelineCreateError

    case class NotAllInputsAreConnected(untappedStepInputs: Seq[UntappedStepInputs]) extends GenericPipelineCreateError

    case class ParameterNotProvided(
      operatorId: String,
      operatorName: String,
      paramName: String
    ) extends GenericPipelineCreateError

    case class InvalidAssetReferenceValueType(
      operatorId: String,
      operatorName: String,
      paramName: String
    ) extends GenericPipelineCreateError

    case class GenericPipelineServiceError(error: PipelineServiceError) extends GenericPipelineCreateError

  }

}
