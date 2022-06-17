package baile.routes.contract.tabular.prediction

import play.api.libs.json.{ Format, Json }

case class ColumnIdentifier(
  tableId: String,
  columnName: String
)

object ColumnIdentifier {
  implicit val ColumnIdentifierFormat: Format[ColumnIdentifier] = Json.format[ColumnIdentifier]
}
