package baile.domain.pipeline

case class PipelineStepInfo(
  step: PipelineStep,
  pipelineParameters: Map[String, String]
)
