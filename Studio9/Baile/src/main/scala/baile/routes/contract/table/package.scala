package baile.routes.contract

import baile.domain.table._
import baile.utils.json.{ EnumFormatBuilder, EnumWritesBuilder }
import play.api.libs.json._

package object table {

  implicit val ColumnDataTypeFormat: Format[ColumnDataType] = EnumFormatBuilder.build(
    {
      case "STRING" => ColumnDataType.String
      case "INTEGER" => ColumnDataType.Integer
      case "BOOLEAN" => ColumnDataType.Boolean
      case "DOUBLE" => ColumnDataType.Double
      case "LONG" => ColumnDataType.Long
      case "TIMESTAMP" => ColumnDataType.Timestamp
    },
    {
      case ColumnDataType.String => "STRING"
      case ColumnDataType.Integer => "INTEGER"
      case ColumnDataType.Boolean => "BOOLEAN"
      case ColumnDataType.Double => "DOUBLE"
      case ColumnDataType.Long => "LONG"
      case ColumnDataType.Timestamp => "TIMESTAMP"
    },
    "column data type"
  )

  implicit val ColumnVariableTypeFormat: Format[ColumnVariableType] = EnumFormatBuilder.build(
    {
      case "CONTINUOUS" => ColumnVariableType.Continuous
      case "CATEGORICAL" => ColumnVariableType.Categorical
    },
    {
      case ColumnVariableType.Continuous => "CONTINUOUS"
      case ColumnVariableType.Categorical => "CATEGORICAL"
    },
    "column variable type"
  )

  implicit val ColumnAlignFormat: Format[ColumnAlign] = EnumFormatBuilder.build(
    {
      case "LEFT" => ColumnAlign.Left
      case "RIGHT" => ColumnAlign.Right
      case "CENTER" => ColumnAlign.Center
    },
    {
      case ColumnAlign.Left => "LEFT"
      case ColumnAlign.Right => "RIGHT"
      case ColumnAlign.Center => "CENTER"
    },
    "column align"
  )

  implicit val TableStatusWrites: Writes[TableStatus] = EnumWritesBuilder.build {
    case TableStatus.Saving => "SAVING"
    case TableStatus.Active => "ACTIVE"
    case TableStatus.Inactive => "INACTIVE"
    case TableStatus.Error => "ERROR"
  }

  implicit val TableStatisticsStatusWrites: Writes[TableStatisticsStatus] = EnumWritesBuilder.build {
    case TableStatisticsStatus.Pending => "PENDING"
    case TableStatisticsStatus.Error => "ERROR"
    case TableStatisticsStatus.Done => "DONE"
  }

  implicit val TableTypeWrites: Writes[TableType] = EnumWritesBuilder.build {
    case TableType.Source => "SOURCE"
    case TableType.Derived => "DERIVED"
  }

}
