package baile.domain.tabular.model

import baile.domain.table.{ ColumnDataType, ColumnVariableType }

case class ModelColumn(
  name: String,
  displayName: String,
  dataType: ColumnDataType,
  variableType: ColumnVariableType
)
