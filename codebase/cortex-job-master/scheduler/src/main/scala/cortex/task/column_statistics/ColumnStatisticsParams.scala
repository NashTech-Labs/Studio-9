package cortex.task.column_statistics

import cortex.JsonSupport.SnakeJson
import cortex.task.TabularAccessParams
import cortex.task.column.{ ColumnDataType, ColumnVariableType }
import cortex.task.tabular_data.Table
import cortex.{ TaskParams, TaskResult }
import play.api.libs.json._

object ColumnStatisticsParams {

  case class ColumnStatisticsTaskParams(
      accessParams:    TabularAccessParams,
      table:           Table,
      histogramLength: Int,
      columns:         Seq[ColumnInfo]
  ) extends TaskParams

  case class ColumnStatisticsTaskResult(
      columns:   Seq[ColumnStatistics],
      rowsCount: Long
  ) extends TaskResult

  case class ColumnInfo(
      name:         String,
      dataType:     ColumnDataType,
      variableType: ColumnVariableType
  )

  case class ColumnStatistics(
      name:                  String,
      numericalStatistics:   Option[NumericalStatistics]   = None,
      categoricalStatistics: Option[CategoricalStatistics] = None
  )

  case class NumericalStatistics(
      min:           Double,
      max:           Double,
      avg:           Double,
      std:           Double,
      stdPopulation: Double,
      mean:          Double,
      histogram:     Seq[NumericalHistogramRow]
  )

  case class CategoricalStatistics(
      uniqueValuesCount: Long,
      histogram:         Seq[CategoricalHistogramRow]
  )

  case class NumericalHistogramRow(
      min:   Double,
      max:   Double,
      count: Long
  )

  case class CategoricalHistogramRow(
      value: Option[String],
      count: Long
  )

  private implicit val numericalHistogramRowReads: Format[NumericalHistogramRow] = SnakeJson.format[NumericalHistogramRow]
  private implicit val categoricalHistogramRowReads: Format[CategoricalHistogramRow] = SnakeJson.format[CategoricalHistogramRow]
  private implicit val categoricalStatisticsReads: Reads[CategoricalStatistics] = SnakeJson.reads[CategoricalStatistics]
  private implicit val numericalStatisticsReads: Reads[NumericalStatistics] = SnakeJson.reads[NumericalStatistics]
  private implicit val columnInfoWrites: Writes[ColumnInfo] = SnakeJson.writes[ColumnInfo]
  private implicit val columnStatisticsReads: Reads[ColumnStatistics] = SnakeJson.reads[ColumnStatistics]
  implicit val columnStatisticsTaskParamsWrites: OWrites[ColumnStatisticsTaskParams] = SnakeJson.writes[ColumnStatisticsTaskParams]
  implicit val columnStatisticsTaskResultReads: Reads[ColumnStatisticsTaskResult] = SnakeJson.reads[ColumnStatisticsTaskResult]

}
