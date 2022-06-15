package baile.services.pipeline

import baile.daocommons.WithId
import baile.domain.pipeline.PipelineParams._
import baile.domain.pipeline._
import baile.services.pipeline.PipelineValidationError._
import cats.implicits._

object PipelineValidator {

  type ErrorOr[R] = Either[PipelineValidationError, R]

  def validatePipelineSteps(
    steps: Seq[PipelineStep],
    pipelineOperators: Map[String, WithId[PipelineOperator]]
  ): ErrorOr[Unit] =
    validatePipelineStepInfos(
      stepInfos = steps.map(PipelineStepInfo(_, Map.empty)),
      pipelineOperators = pipelineOperators
    )

  def validatePipelineStepInfos(
    stepInfos: Seq[PipelineStepInfo],
    pipelineOperators: Map[String, WithId[PipelineOperator]]
  ): ErrorOr[Unit] = {
    val steps = stepInfos.map(_.step)
    for {
      _ <- validateStepIdsUniqueness(steps.map(_.id))
      _ <- stepInfos.toList.foldM[ErrorOr, Unit](
        ().asRight[PipelineValidationError]
      ) {
        case (_, PipelineStepInfo(step, pipelineParameters)) =>
          for {
            operator <- Either.fromOption(
              pipelineOperators.get(step.operatorId),
              OperatorNotFound(step.operatorId)
            )
            _ <- validateStepInputsWithOperator(steps, step, operator, pipelineOperators)
            _ <- validateParams(operator.entity.params, step.params, pipelineParameters.keys.toSeq)
          } yield ()
      }
      _ <- validatePipelineIsAcyclic(steps)
    } yield ()
  }

  private def validateStepIdsUniqueness(stepIds: Seq[String]): ErrorOr[Unit] = {
    Either.cond(
      stepIds.distinct.size == stepIds.size,
      (),
      StepsIdsAreNotUnique
    )
  }

  private[pipeline] def dataTypesAreCompatible(
    from: PipelineDataType,
    to: PipelineDataType,
    covariate: Boolean
  ): Boolean = {
    (from, to) match {
      case (complexFrom: ComplexDataType, complexTo: ComplexDataType) =>
        complexDataTypesAreCompatible(
          from = complexFrom,
          to = complexTo,
          covariate
        )

      case _ => from == to
    }
  }

  private def complexDataTypesAreCompatible(
    from: ComplexDataType,
    to: ComplexDataType,
    covariate: Boolean
  ): Boolean = {

    def findCompatibleFrom(from: ComplexDataType): Option[ComplexDataType] = {
      if (from.definition == to.definition) {
        Some(from)
      } else if (covariate) {
        from.parents.toList.collectFirstSome(findCompatibleFrom)
      } else {
        None
      }
    }

    findCompatibleFrom(from) match {
      case Some(compatibleFrom) =>
        val typeArgumentsFrom = compatibleFrom.typeArguments
        val typeArgumentsTo = to.typeArguments

        typeArgumentsFrom.length == typeArgumentsTo.length &&
          typeArgumentsFrom.zip(typeArgumentsTo).forall {
            case (typeArgumentFrom, typeArgumentTo) =>
              dataTypesAreCompatible(
                from = typeArgumentFrom,
                to = typeArgumentTo,
                covariate = covariate
              )
          }

      case None => false
    }
  }

  private[pipeline] def validateParams(
    operatorParameters: Seq[OperatorParameter],
    stepParams: PipelineParams,
    pipelineParameters: Seq[String]
  ): ErrorOr[Unit] = {
    val operatorParametersMap = operatorParameters.map(param => param.name -> param).toMap

    def validateParamValues(
      paramValue: PipelineParam,
      operatorParameter: OperatorParameter
    ): ErrorOr[Unit] = {
      (paramValue, operatorParameter.typeInfo) match {
        case (stringParams: StringParams, typeInfo: StringParameterTypeInfo) if operatorParameter.multiple =>
          validateParamValuesAreInList(stringParams.values, typeInfo.values)
        case (intParams: IntParams, typeInfo: IntParameterTypeInfo) if operatorParameter.multiple =>
          validateNumericValues(intParams.values, typeInfo.min, typeInfo.max, typeInfo.values)
        case (floatParams: FloatParams, typeInfo: FloatParameterTypeInfo) if operatorParameter.multiple =>
          validateNumericValues(floatParams.values, typeInfo.min, typeInfo.max, typeInfo.values)
        case (intParams: IntParams, typeInfo: FloatParameterTypeInfo) if operatorParameter.multiple =>
          validateNumericValues[Float](intParams.values.map(_.toFloat), typeInfo.min, typeInfo.max, typeInfo.values)
        case (stringParam: StringParam, typeInfo: StringParameterTypeInfo) =>
          validateParamValuesAreInList(Seq(stringParam.value), typeInfo.values)
        case (intParam: IntParam, typeInfo: IntParameterTypeInfo) =>
          validateNumericValues(Seq(intParam.value), typeInfo.min, typeInfo.max, typeInfo.values)
        case (floatParam: FloatParam, typeInfo: FloatParameterTypeInfo) =>
          validateNumericValues(Seq(floatParam.value), typeInfo.min, typeInfo.max, typeInfo.values)
        case (intParam: IntParam, typeInfo: FloatParameterTypeInfo) =>
          validateNumericValues[Float](Seq(intParam.value), typeInfo.min, typeInfo.max, typeInfo.values)
        case (_: BooleanParams, _: BooleanParameterTypeInfo) if operatorParameter.multiple => ().asRight
        case (_: BooleanParam, _: BooleanParameterTypeInfo) => ().asRight
        case (_: StringParam, _: AssetParameterTypeInfo) => ().asRight
        case (_: StringParams, _: AssetParameterTypeInfo) if operatorParameter.multiple => ().asRight
        case (EmptySeqParam, _) if operatorParameter.multiple => ().asRight
        case (unknown, expected) =>
          InvalidParamValue(s"Provided value [$unknown] is not expected for [$expected]").asLeft
      }
    }

    def validatePipelineParams: ErrorOr[Unit] =
      pipelineParameters
        .find(param => !operatorParametersMap.isDefinedAt(param))
        .map(PipelineParamNotFound)
        .toLeft(())

    def validateStepParams: ErrorOr[Unit] = {
      def validateConditionsIfNeeded(operatorParameter: OperatorParameter,
        stepParams: PipelineParams
      ): ErrorOr[Unit] =
        if (operatorParameter.conditions.keys.exists(pipelineParameters.contains)) ().asRight
        else validateConditions(operatorParameter, stepParams)

      stepParams
        .toList
        .foldM[ErrorOr, Unit](().asRight[PipelineValidationError]) { case (_, (paramName, paramValue)) =>
          operatorParametersMap.get(paramName) match {
            case Some(operatorParameter) =>
              for {
                _ <- validateParamValues(paramValue, operatorParameter)
                _ <- validateConditionsIfNeeded(operatorParameter, stepParams)
              } yield ()
            case None => PipelineParamNotFound(paramName).asLeft
          }
        }
    }

    def validatePipelineParametersDerivedParameters: ErrorOr[Unit] =
      operatorParameters
        .toList
        .foldM[ErrorOr, Unit](().asRight[PipelineValidationError]) { case (_, operatorParameter) =>
          val isDefined = stepParams.isDefinedAt(operatorParameter.name) ||
            pipelineParameters.contains(operatorParameter.name) ||
            operatorParameterHasDefault(operatorParameter.typeInfo)

          operatorParameter.conditions.keys.find(pipelineParameters.contains) match {
            case Some(paramName) if !isDefined =>
              DerivedConditionalParameterMissing(
                operatorParameter.name,
                paramName
              ).asLeft
            case _ => ().asRight
          }
        }

    for {
      _ <- validatePipelineParams
      _ <- validateStepParams
      _ <- validatePipelineParametersDerivedParameters
    } yield ()
  }

  // validates that operatorParameter conditions match.
  // If not - the parameter is not expected to be provided by the user
  private[pipeline] def validateConditions(
    operatorParameter: OperatorParameter,
    stepParams: PipelineParams
  ): ErrorOr[Unit] = {
    operatorParameter.conditions.toList.foldM[ErrorOr, Unit](().asRight[PipelineValidationError]) {
      case (_, (paramName, condition)) =>
        stepParams.get(paramName) match {
          case Some(param) =>
            val result = (param, condition) match {
              case (StringParam(value), StringParameterCondition(allowedValues)) =>
                validateParamValuesAreInList(Seq(value), allowedValues)
              case (StringParams(values), StringParameterCondition(allowedValues)) =>
                validateParamValuesAreInList(values, allowedValues)

              case (IntParam(value), IntParameterCondition(allowedValues, min, max)) =>
                validateNumericValues(Seq(value), min, max, allowedValues)
              case (IntParams(values), IntParameterCondition(allowedValues, min, max)) =>
                validateNumericValues(values, min, max, allowedValues)

              case (FloatParam(value), FloatParameterCondition(allowedValues, min, max)) =>
                validateNumericValues(Seq(value), min, max, allowedValues)
              case (FloatParams(values), FloatParameterCondition(allowedValues, min, max)) =>
                validateNumericValues(values, min, max, allowedValues)

              case (BooleanParam(value), BooleanParameterCondition(allowedValue)) =>
                validateParamValuesAreInList(Seq(value), Seq(allowedValue))
              case (BooleanParams(values), BooleanParameterCondition(allowedValue)) =>
                validateParamValuesAreInList(values, Seq(allowedValue))

              case _ =>
                InvalidParamValue(s"Provided value $param is not compatible with condition $condition").asLeft
            }

            result.leftMap { error: InvalidParamValue =>
              ParamConditionNotSatisfied(operatorParameter.name, paramName, error.errorMsg)
            }

          case None =>
            ParamConditionNotSatisfied(
              operatorParameter.name,
              paramName,
              s"Param $paramName is not provided"
            ).asLeft
        }
    }
  }

  private def validateParamValuesAreInList[T](
    values: Seq[T],
    allowedValues: Seq[T]
  ): Either[InvalidParamValue, Unit] =
    Either.cond(
      allowedValues.isEmpty || values.forall(allowedValues.contains(_)),
      (),
      InvalidParamValue(s"Expected values are $allowedValues")
    )

  private def validateParamValueAreInRange[T](
    values: Seq[T],
    min: Option[T],
    max: Option[T]
  )(implicit env: T => Ordered[T]): Either[InvalidParamValue, Unit] =
    Either.cond(
      min.forall(_ <= values.min) && max.forall(_ >= values.max),
      (),
      InvalidParamValue(s"Value should be in range: $min - $max")
    )

  private def validateNumericValues[T](
    values: Seq[T],
    min: Option[T],
    max: Option[T],
    allowedValues: Seq[T]
  )(implicit env: T => Ordered[T]): Either[InvalidParamValue, Unit] =
    for {
      _ <- validateParamValuesAreInList(values, allowedValues)
      _ <- validateParamValueAreInRange(values, min, max)
    } yield ()

  private[pipeline] def validateStepInputsWithOperator(
    steps: Seq[PipelineStep],
    step: PipelineStep,
    stepOperator: WithId[PipelineOperator],
    pipelineOperators: Map[String, WithId[PipelineOperator]]
  ): ErrorOr[Unit] = {
    step.inputs.toList.foldLeft(
      ().asRight[PipelineValidationError]
    ) {
      case (_, (inputName, outputReference)) =>
        stepOperator.entity.inputs.find(_.name == inputName) match {
          case Some(operatorInputReference) => steps.find(_.id == outputReference.stepId) match {
            case Some(inputStep) =>
              for {
                inputOperator <- Either.fromOption(
                  pipelineOperators.get(inputStep.operatorId),
                  OperatorNotFound(inputStep.operatorId): PipelineValidationError
                )
                output <- Either.fromOption(
                  inputOperator.entity.outputs.lift(outputReference.outputIndex),
                  InvalidOutputReference(
                    inputOperator.id,
                    inputOperator.entity.name,
                    outputReference.outputIndex
                  ): PipelineValidationError
                )
                _ <- Either.cond(
                  dataTypesAreCompatible(
                    from = output.`type`,
                    to = operatorInputReference.`type`,
                    covariate = operatorInputReference.covariate
                  ),
                  (),
                  IncompatibleInput(operatorInputReference.`type`, output.`type`): PipelineValidationError
                )
              } yield ()
            case None => StepNotFound(outputReference.stepId).asLeft
          }
          case None => InvalidInput(stepOperator.id, stepOperator.entity.name, inputName).asLeft
        }
    }
  }

  private[pipeline] def operatorParameterHasDefault(typeInfo: ParameterTypeInfo): Boolean = {
    typeInfo match {
      case typeInfo: StringParameterTypeInfo =>
        typeInfo.default.nonEmpty
      case typeInfo: BooleanParameterTypeInfo =>
        typeInfo.default.nonEmpty
      case typeInfo: FloatParameterTypeInfo =>
        typeInfo.default.nonEmpty
      case typeInfo: IntParameterTypeInfo =>
        typeInfo.default.nonEmpty
      case _: AssetParameterTypeInfo =>
        false
    }
  }

  private def validatePipelineIsAcyclic(steps: Seq[PipelineStep]): ErrorOr[Unit] = {
    import scalax.collection.Graph
    import scalax.collection.GraphPredef._

    val stepsMap = steps.map { step => step.id -> step }.toMap
    val edges = steps.flatMap { step =>
      step.inputs.values.map { outputRef =>
        stepsMap(outputRef.stepId) ~> step
      }
    }

    if (Graph.from(steps, edges).isAcyclic) {
      ().asRight[PipelineValidationError]
    } else {
      CircularDependency.asLeft
    }
  }

}
