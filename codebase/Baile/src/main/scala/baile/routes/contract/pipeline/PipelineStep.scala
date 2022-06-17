package baile.routes.contract.pipeline

import baile.domain.pipeline.{ PipelineStep => DomainPipelineStep }
import play.api.libs.json.{ Json, OFormat }

case class PipelineStep(
  id: String,
  operator: String,
  inputs: Map[String, PipelineOutputReference],
  params: PipelineParams,
  coordinates: Option[PipelineCoordinates]
) {

  def toDomain: DomainPipelineStep =
    DomainPipelineStep(
      id,
      operator,
      inputs.mapValues(_.toDomain),
      params.toDomain,
      coordinates.map(_.toDomain)
    )

}

object PipelineStep {

  def fromDomain(pipelineStep: DomainPipelineStep): PipelineStep = {
    PipelineStep(
      pipelineStep.id,
      pipelineStep.operatorId,
      pipelineStep.inputs.mapValues(PipelineOutputReference.fromDomain),
      PipelineParams.fromDomain(pipelineStep.params),
      pipelineStep.coordinates.map(PipelineCoordinates.fromDomain)
    )
  }

  implicit val PipelineStepFormat: OFormat[PipelineStep] = Json.format[PipelineStep]

}
