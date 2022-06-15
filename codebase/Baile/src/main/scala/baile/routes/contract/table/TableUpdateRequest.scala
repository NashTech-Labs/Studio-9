package baile.routes.contract.table

import play.api.libs.json.{ Json, Reads }

case class TableUpdateRequest(
  name: Option[String],
  columns: Seq[UpdateColumnRequest],
  description: Option[String]
)

object TableUpdateRequest {
  implicit val TableUpdateRequestReads: Reads[TableUpdateRequest] = Json.reads[TableUpdateRequest]
}

