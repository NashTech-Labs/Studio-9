package baile.routes.contract.table

import baile.daocommons.WithId
import baile.domain.table.Table
import play.api.libs.json.{ Json, OWrites }

case class ExtendedTableResponse(
  table: TableResponse,
  columns: Seq[TableColumnResponse]
)

object ExtendedTableResponse {
  implicit val ExtendedTableResponseWrites: OWrites[ExtendedTableResponse] =
    OWrites {
      case ExtendedTableResponse(table, columns) =>
        Json.toJsObject(table) + ("columns" -> Json.toJson(columns))
    }

  def fromDomain(table: WithId[Table]): ExtendedTableResponse =
    ExtendedTableResponse(
      table = TableResponse.fromDomain(table),
      columns = table.entity.columns.map(TableColumnResponse.fromDomain)
    )
}
