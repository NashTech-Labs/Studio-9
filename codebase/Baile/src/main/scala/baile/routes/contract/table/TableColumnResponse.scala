package baile.routes.contract.table

import baile.domain.table._
import play.api.libs.json.{ Json, OWrites }

case class TableColumnResponse(
  name: String,
  displayName: String,
  dataType: ColumnDataType,
  variableType: ColumnVariableType,
  align: ColumnAlign
)

object TableColumnResponse {
  implicit val TableColumnResponseWrites: OWrites[TableColumnResponse] = Json.writes[TableColumnResponse]

  def fromDomain(column: Column): TableColumnResponse = {
    TableColumnResponse(
      name = column.name,
      displayName = column.displayName,
      dataType = column.dataType,
      variableType = column.variableType,
      align = column.align
    )
  }
}
