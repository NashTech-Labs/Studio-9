package baile.routes.contract.pipeline

import baile.domain.pipeline.result.ConfusionMatrix
import baile.routes.contract.common.ConfusionMatrixCellResponse
import play.api.libs.json.{ Json, OWrites }

case class ConfusionMatrixResponse(
  rows: Seq[ConfusionMatrixCellResponse],
  labels: Seq[String]
) extends PipelineOperatorApplicationSummaryResponse

object ConfusionMatrixResponse {

  def fromDomain(in: ConfusionMatrix): ConfusionMatrixResponse = {
    ConfusionMatrixResponse(
      in.confusionMatrixCells.map(ConfusionMatrixCellResponse.fromDomain),
      in.labels
    )
  }

  implicit val ConfusionMatrixResponseWrites: OWrites[ConfusionMatrixResponse] =
    Json.writes[ConfusionMatrixResponse]

}
