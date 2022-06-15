package baile.routes.contract.pipeline

import baile.domain.pipeline.{ PipelineOutputReference => DomainPipelineOutputReference }
import play.api.libs.json.{ Json, OFormat }

case class PipelineOutputReference(
  stepId: String,
  outputIndex: Int
) {

  def toDomain: DomainPipelineOutputReference =
    DomainPipelineOutputReference(
      stepId,
      outputIndex
    )
}

object PipelineOutputReference {

  def fromDomain(reference: DomainPipelineOutputReference): PipelineOutputReference = {
    PipelineOutputReference(
      reference.stepId,
      reference.outputIndex
    )
  }

  implicit val PipelineOutputReferenceFormat: OFormat[PipelineOutputReference] = Json.format[PipelineOutputReference]

}
