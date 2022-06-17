package baile.services.tabular.model.util.export.format.v1

case class ModelColumn(
  name: String,
  displayName: String,
  dataType: ColumnDataType,
  variableType: ColumnVariableType
)
