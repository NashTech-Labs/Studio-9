package baile.domain.pipeline

case class PipelineOperator(
  name: String,
  description: Option[String],
  category: Option[String],
  className: String,
  moduleName: String,
  packageId: String,
  inputs: Seq[PipelineOperatorInput],
  outputs: Seq[PipelineOperatorOutput],
  params: Seq[OperatorParameter]
)
