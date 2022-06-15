package baile.routes.contract.table

import play.api.libs.json.{ Json, OWrites }

case class TableColumnHistogramResponse(
  count: Long,
  value: Option[String],
  min: Option[Double],
  max: Option[Double]
)

object TableColumnHistogramResponse {

  implicit val TableColumnHistogramResponseWrites: OWrites[TableColumnHistogramResponse] =
    Json.writes[TableColumnHistogramResponse]

}
