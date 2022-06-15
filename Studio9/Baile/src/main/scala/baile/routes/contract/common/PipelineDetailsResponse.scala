package baile.routes.contract.common

import baile.domain.job.PipelineTiming
import play.api.libs.json.{ Json, OWrites }

case class PipelineDetailsResponse(
  time: Long,
  description: String
)

object PipelineDetailsResponse {

  def fromDomain(pipelineTiming: PipelineTiming): PipelineDetailsResponse =
    PipelineDetailsResponse(
      time = pipelineTiming.time,
      description = pipelineTiming.description
    )

  implicit val PipelineDetailsResponseWrites: OWrites[PipelineDetailsResponse] =
    Json.writes[PipelineDetailsResponse]

}
