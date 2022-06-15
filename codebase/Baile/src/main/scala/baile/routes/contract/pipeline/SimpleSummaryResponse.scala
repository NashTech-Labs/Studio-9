package baile.routes.contract.pipeline

import baile.domain.pipeline.result.SimpleSummary
import play.api.libs.json.{ Json, OWrites }

case class SimpleSummaryResponse(
  values: Map[String, PipelineResultValueResponse]
) extends PipelineOperatorApplicationSummaryResponse

object SimpleSummaryResponse {

  def fromDomain(in: SimpleSummary): SimpleSummaryResponse = SimpleSummaryResponse(
    values = in.values.mapValues(PipelineResultValueResponse.fromDomain)
  )

  implicit val SimpleSummaryResponseWrites: OWrites[SimpleSummaryResponse] =
    Json.writes[SimpleSummaryResponse]

}
