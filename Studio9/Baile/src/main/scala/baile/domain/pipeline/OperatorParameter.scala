package baile.domain.pipeline

case class OperatorParameter(
  name: String,
  description: Option[String],
  multiple: Boolean,
  typeInfo: ParameterTypeInfo,
  conditions: Map[String, ParameterCondition]
)
