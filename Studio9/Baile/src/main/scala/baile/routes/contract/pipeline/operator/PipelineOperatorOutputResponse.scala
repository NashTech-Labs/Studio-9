package baile.routes.contract.pipeline.operator

import baile.domain.pipeline.PipelineOperatorOutput
import play.api.libs.json.{ Json, OWrites }

case class PipelineOperatorOutputResponse(
  description: Option[String],
  `type`: PipelineDataTypeResponse
)

object PipelineOperatorOutputResponse {
  implicit val PipelineOperatorResponseWrites: OWrites[PipelineOperatorOutputResponse] =
    Json.writes[PipelineOperatorOutputResponse]

  def fromDomain(pipelineOperatorOutput: PipelineOperatorOutput): PipelineOperatorOutputResponse =
    PipelineOperatorOutputResponse(
      `type` = PipelineDataTypeResponse.fromDomain(pipelineOperatorOutput.`type`),
      description = pipelineOperatorOutput.description
    )
}
