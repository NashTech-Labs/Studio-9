package baile.routes.contract.pipeline

import baile.domain.pipeline.{ PipelineStepInfo => DomainPipelineStepInfo }
import play.api.libs.functional.syntax._
import play.api.libs.json._

case class PipelineStepInfo(
  step: PipelineStep,
  pipelineParameters: Map[String, String]
) {

  def toDomain: DomainPipelineStepInfo =
    DomainPipelineStepInfo(
      step = step.toDomain,
      pipelineParameters = pipelineParameters
    )

}

object PipelineStepInfo {

  def fromDomain(in: DomainPipelineStepInfo): PipelineStepInfo =
    PipelineStepInfo(
      step = PipelineStep.fromDomain(in.step),
      pipelineParameters = in.pipelineParameters
    )

  implicit val PipelineStepInfoFormat: OFormat[PipelineStepInfo] = (
    __.format[PipelineStep] ~
      (__ \ "pipelineParameters").format[Map[String, String]]
    ) (PipelineStepInfo.apply, unlift(PipelineStepInfo.unapply))

}
