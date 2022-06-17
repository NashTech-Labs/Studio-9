package baile.services.tabular.model.util.export.format.v1

sealed trait ColumnVariableType

object ColumnVariableType {

  case object Continuous extends ColumnVariableType

  case object Categorical extends ColumnVariableType

}
