package sqlserver.routes.contract.query

import play.api.libs.json.{ Json, OWrites }
import sqlserver.domain.table.{ Column, ColumnDataType }

case class ColumnResponse(
  name: String,
  `type`: ColumnDataType
)

object ColumnResponse {

  def fromDomain(column: Column): ColumnResponse =
    ColumnResponse(
      name = column.name,
      `type` = column.dataType
    )

  implicit val ColumnResponseWrites: OWrites[ColumnResponse] = Json.writes[ColumnResponse]

}
