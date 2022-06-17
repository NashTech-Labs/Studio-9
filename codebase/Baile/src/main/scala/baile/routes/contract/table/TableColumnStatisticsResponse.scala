package baile.routes.contract.table

import baile.domain.table.{ CategoricalStatistics, ColumnStatistics, NumericalStatistics }
import play.api.libs.json.{ Json, OWrites }

case class TableColumnStatisticsResponse(
  columnName: String,
  min: Option[Double],
  max: Option[Double],
  avg: Option[Double],
  std: Option[Double],
  stdPopulation: Option[Double],
  mean: Option[Double],
  uniqueCount: Option[Long],
  mostFrequentValue: Option[String],
  histogram: Seq[TableColumnHistogramResponse]
)

object TableColumnStatisticsResponse {

  implicit val TableColumnStatisticResponseWrites: OWrites[TableColumnStatisticsResponse] =
    Json.writes[TableColumnStatisticsResponse]

  def fromDomain(columnName: String, statistic: ColumnStatistics): TableColumnStatisticsResponse = statistic match {
    case NumericalStatistics(min, max, avg, std, stdPopulation, mean, numericalHistogram) =>
      TableColumnStatisticsResponse(
        columnName = columnName,
        min = Some(min),
        max = Some(max),
        avg = Some(avg),
        std = Some(std),
        stdPopulation = Some(stdPopulation),
        mean = Some(mean),
        uniqueCount = None,
        mostFrequentValue = None,
        histogram = numericalHistogram.rows.map { row =>
          TableColumnHistogramResponse(
            count = row.count,
            value = None,
            min = Some(row.min),
            max = Some(row.max)
          )
        }
      )
    case CategoricalStatistics(uniqueValuesCount, categoricalHistogram) =>
      TableColumnStatisticsResponse(
        columnName = columnName,
        min = None,
        max = None,
        avg = None,
        std = None,
        mean = None,
        uniqueCount = Some(uniqueValuesCount),
        stdPopulation = None,
        mostFrequentValue = categoricalHistogram.rows.sortBy(_.count).headOption.flatMap(_.value),
        histogram = categoricalHistogram.rows.map { row =>
          TableColumnHistogramResponse(
            count = row.count,
            value = row.value,
            min = None,
            max = None
          )
        }
      )
  }

}
