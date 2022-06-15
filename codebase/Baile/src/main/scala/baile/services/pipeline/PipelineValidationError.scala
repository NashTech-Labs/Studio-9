package baile.services.pipeline

import baile.domain.pipeline.PipelineDataType

sealed trait PipelineValidationError

object PipelineValidationError {

  case object StepsIdsAreNotUnique extends PipelineValidationError

  case class OperatorNotFound(operatorId: String) extends PipelineValidationError

  case class StepNotFound(stepId: String) extends PipelineValidationError

  case class InvalidInput(operatorId: String, operatorName: String, inputName: String) extends PipelineValidationError

  case class IncompatibleInput(
    actualDataType: PipelineDataType,
    providedDataType: PipelineDataType
  ) extends PipelineValidationError

  case class InvalidOutputReference(
    operatorId: String,
    operatorName: String,
    outputIndex: Int
  ) extends PipelineValidationError

  case class PipelineParamNotFound(paramName: String) extends PipelineValidationError

  case class InvalidParamValue(errorMsg: String) extends PipelineValidationError

  case class ParamConditionNotSatisfied(
    conditionParam: String,
    paramName: String,
    errorMsg: String
  ) extends PipelineValidationError

  case class DerivedConditionalParameterMissing(
    conditionalParamName: String,
    paramName: String
  ) extends PipelineValidationError

  case object CircularDependency extends PipelineValidationError

}
