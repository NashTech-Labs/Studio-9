package baile.routes.contract.common

import baile.domain.common.ConfusionMatrixCell
import play.api.libs.json.{ Json, OWrites }

case class ConfusionMatrixCellResponse(
  actual: Option[Int],
  predicted: Option[Int],
  count: Int
)

object ConfusionMatrixCellResponse {

  implicit val ConfusionMatrixCellResponseWrites: OWrites[ConfusionMatrixCellResponse] =
    Json.writes[ConfusionMatrixCellResponse]

  def fromDomain(in: ConfusionMatrixCell): ConfusionMatrixCellResponse = ConfusionMatrixCellResponse(
    actual = in.actualLabel,
    predicted = in.predictedLabel,
    count = in.count
  )
}
