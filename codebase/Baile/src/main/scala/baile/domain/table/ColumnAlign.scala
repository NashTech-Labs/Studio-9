package baile.domain.table

sealed trait ColumnAlign

object ColumnAlign {

  case object Left extends ColumnAlign

  case object Right extends ColumnAlign

  case object Center extends ColumnAlign

}
