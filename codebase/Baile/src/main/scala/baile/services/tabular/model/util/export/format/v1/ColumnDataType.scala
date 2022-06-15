package baile.services.tabular.model.util.export.format.v1

sealed trait ColumnDataType

object ColumnDataType {

  case object String extends ColumnDataType

  case object Integer extends ColumnDataType

  case object Boolean extends ColumnDataType

  case object Double extends ColumnDataType

  case object Long extends ColumnDataType

  case object Timestamp extends ColumnDataType

}
