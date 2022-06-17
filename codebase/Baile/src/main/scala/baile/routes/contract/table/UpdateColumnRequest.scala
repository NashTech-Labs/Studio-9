package baile.routes.contract.table

import baile.domain.table.{ ColumnAlign, ColumnVariableType }
import play.api.libs.json.{ Json, Reads }

case class UpdateColumnRequest(
  name: String,
  displayName: Option[String],
  variableType: Option[ColumnVariableType],
  align: Option[ColumnAlign]
)

object UpdateColumnRequest {

  implicit val UpdateColumnRequestReads: Reads[UpdateColumnRequest] = Json.reads[UpdateColumnRequest]

}
