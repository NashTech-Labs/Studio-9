package baile.domain.pipeline.result

case class SimpleSummary(
  values: Map[String, PipelineResultValue]
) extends PipelineOperatorApplicationSummary
