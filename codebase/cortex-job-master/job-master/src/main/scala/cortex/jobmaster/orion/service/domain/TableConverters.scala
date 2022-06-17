package cortex.jobmaster.orion.service.domain

import cortex.CortexException
import cortex.api.job.table.{ DataSource, DataType, VariableType }
import cortex.task.column.{ ColumnDataType, ColumnVariableType }
import cortex.task.tabular_data.Table

object TableConverters {

  def apiDataSourceToTable(dataSource: DataSource): Table = {
    val t = dataSource.table
      .getOrElse(throw new CortexException("table is empty"))
      .meta
      .getOrElse(throw new CortexException("table does not contain meta"))
    Table(
      schema = t.schema,
      name   = t.name
    )
  }

  def apiDataTypeToDomain(dataType: DataType): ColumnDataType = dataType match {
    case DataType.STRING          => ColumnDataType.STRING
    case DataType.INTEGER         => ColumnDataType.INTEGER
    case DataType.DOUBLE          => ColumnDataType.DOUBLE
    case DataType.BOOLEAN         => ColumnDataType.BOOLEAN
    case DataType.TIMESTAMP       => ColumnDataType.TIMESTAMP
    case DataType.LONG            => ColumnDataType.LONG
    case DataType.Unrecognized(_) => throw new CortexException(s"Invalid data type $dataType")
  }

  def apiVariableTypeToDomain(variableType: VariableType): ColumnVariableType = variableType match {
    case VariableType.CATEGORICAL     => ColumnVariableType.CATEGORICAL
    case VariableType.CONTINUOUS      => ColumnVariableType.CONTINUOUS
    case VariableType.Unrecognized(_) => throw new RuntimeException(s"Invalid variable type $variableType")
  }
}
