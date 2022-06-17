package baile.domain.pipeline

case class PipelineOperatorOutput(
  description: Option[String],
  `type`: PipelineDataType
)
