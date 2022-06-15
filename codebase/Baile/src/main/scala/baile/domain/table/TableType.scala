package baile.domain.table

sealed trait TableType

object TableType {

  case object Source extends TableType

  case object Derived extends TableType

}
