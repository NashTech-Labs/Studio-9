package baile.domain.pipeline

case class PipelineOperatorInput(
  name: String,
  description: Option[String],
  `type`: PipelineDataType,
  covariate: Boolean,
  required: Boolean
)
