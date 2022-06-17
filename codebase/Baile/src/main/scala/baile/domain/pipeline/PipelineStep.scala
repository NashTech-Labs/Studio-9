package baile.domain.pipeline

import baile.domain.pipeline.PipelineParams.PipelineParams

case class PipelineStep(
  id: String,
  operatorId: String,
  inputs: Map[String, PipelineOutputReference],
  params: PipelineParams,
  coordinates: Option[PipelineCoordinates]
)
