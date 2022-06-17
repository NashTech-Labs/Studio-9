package sqlserver.services.dremio.datacontract

import play.api.libs.json.Reads
import sqlserver.utils.json.EnumReadsBuilder

sealed trait ColumnTypeNameResponse

object ColumnTypeNameResponse {

  case object Boolean extends ColumnTypeNameResponse
  case object Varbinary extends ColumnTypeNameResponse
  case object Date extends ColumnTypeNameResponse
  case object Float extends ColumnTypeNameResponse
  case object Decimal extends ColumnTypeNameResponse
  case object Double extends ColumnTypeNameResponse
  case object IntervalYear extends ColumnTypeNameResponse
  case object IntervalDay extends ColumnTypeNameResponse
  case object Integer extends ColumnTypeNameResponse
  case object BigInt extends ColumnTypeNameResponse
  case object Time extends ColumnTypeNameResponse
  case object Timestamp extends ColumnTypeNameResponse
  case object Varchar extends ColumnTypeNameResponse
  case object List extends ColumnTypeNameResponse
  case object Struct extends ColumnTypeNameResponse
  case object Union extends ColumnTypeNameResponse
  case object Other extends ColumnTypeNameResponse

  implicit val ColumnTypeNameResponseReads: Reads[ColumnTypeNameResponse] = EnumReadsBuilder.build {
    case "BOOLEAN" => ColumnTypeNameResponse.Boolean
    case "VARBINARY" => ColumnTypeNameResponse.Varbinary
    case "DATE" => ColumnTypeNameResponse.Date
    case "FLOAT" => ColumnTypeNameResponse.Float
    case "DECIMAL" => ColumnTypeNameResponse.Decimal
    case "DOUBLE" => ColumnTypeNameResponse.Double
    case "INTERVAL DAY TO SECOND" => ColumnTypeNameResponse.IntervalDay
    case "INTERVAL YEAR TO MONTH" => ColumnTypeNameResponse.IntervalYear
    case "INTEGER" => ColumnTypeNameResponse.Integer
    case "BIGINT" => ColumnTypeNameResponse.BigInt
    case "TIME" => ColumnTypeNameResponse.Time
    case "TIMESTAMP" => ColumnTypeNameResponse.Timestamp
    case "VARCHAR" => ColumnTypeNameResponse.Varchar
    case "LIST" => ColumnTypeNameResponse.List
    case "STRUCT" => ColumnTypeNameResponse.Struct
    case "UNION" => ColumnTypeNameResponse.Union
    case "OTHER" => ColumnTypeNameResponse.Other
  }

}
