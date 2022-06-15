package baile.domain.table

import baile.daocommons.sorting.Field

case class Column(
  name: String,
  displayName: String,
  dataType: ColumnDataType,
  variableType: ColumnVariableType,
  align: ColumnAlign,
  statistics: Option[ColumnStatistics]
) extends Field
