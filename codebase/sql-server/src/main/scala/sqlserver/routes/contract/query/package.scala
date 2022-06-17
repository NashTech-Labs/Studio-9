package sqlserver.routes.contract

import play.api.libs.json.Writes
import sqlserver.domain.table.ColumnDataType
import sqlserver.utils.json.EnumWritesBuilder

package object query {

  implicit val ColumnDataTypeWrites: Writes[ColumnDataType] = EnumWritesBuilder.build(
    {
      case ColumnDataType.String => "STRING"
      case ColumnDataType.Integer => "INTEGER"
      case ColumnDataType.Boolean => "BOOLEAN"
      case ColumnDataType.Double => "DOUBLE"
      case ColumnDataType.Long => "LONG"
      case ColumnDataType.Timestamp => "TIMESTAMP"
    }
  )

}
