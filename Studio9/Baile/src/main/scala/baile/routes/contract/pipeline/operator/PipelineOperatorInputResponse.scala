package baile.routes.contract.pipeline.operator

import baile.domain.pipeline.PipelineOperatorInput
import play.api.libs.json.{ Json, OWrites }

case class PipelineOperatorInputResponse(
  name: String,
  description: Option[String],
  `type`: PipelineDataTypeResponse,
  covariate: Boolean,
  optional: Boolean
)

object PipelineOperatorInputResponse {
  implicit val PipelineOperatorInputResponseWrites: OWrites[PipelineOperatorInputResponse] =
    Json.writes[PipelineOperatorInputResponse]

  def fromDomain(pipelineOperatorInput: PipelineOperatorInput): PipelineOperatorInputResponse =
    PipelineOperatorInputResponse(
      name = pipelineOperatorInput.name,
      description = pipelineOperatorInput.description,
      `type` = PipelineDataTypeResponse.fromDomain(pipelineOperatorInput.`type`),
      covariate = pipelineOperatorInput.covariate,
      optional = !pipelineOperatorInput.required
    )
}
