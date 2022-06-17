package baile.domain.table

sealed trait ColumnVariableType

object ColumnVariableType {

  case object Continuous extends ColumnVariableType

  case object Categorical extends ColumnVariableType

}
