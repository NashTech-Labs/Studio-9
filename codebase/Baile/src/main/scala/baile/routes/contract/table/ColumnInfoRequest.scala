package baile.routes.contract.table

import baile.domain.table.{ ColumnAlign, ColumnDataType, ColumnVariableType }
import play.api.libs.json.{ Json, JsonValidationError, Reads }

case class ColumnInfoRequest(
  name: String,
  displayName: Option[String],
  variableType: Option[ColumnVariableType],
  dataType: ColumnDataType,
  align: ColumnAlign
)

object ColumnInfoRequest {
  implicit val ColumnInfoReads: Reads[ColumnInfoRequest] = Json.reads[ColumnInfoRequest]
    .filter(JsonValidationError("Column name should be all lower-case latin characters, numbers or '_'")){
      _.name.matches("^[a-z0-9_]+$")
    }
}

