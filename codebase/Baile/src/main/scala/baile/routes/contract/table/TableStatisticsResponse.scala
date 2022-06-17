package baile.routes.contract.table

import baile.domain.table.TableStatisticsStatus
import baile.services.table.TableService.TableStatistics
import play.api.libs.json.{ Json, OWrites }

case class TableStatisticsResponse(
  id: String,
  status: TableStatisticsStatus,
  stats: Seq[TableColumnStatisticsResponse]
)

object TableStatisticsResponse {

  implicit val TableStatisticResponseWrites: OWrites[TableStatisticsResponse] =
    Json.writes[TableStatisticsResponse]

  def fromDomain(statistic: TableStatistics): TableStatisticsResponse =
    TableStatisticsResponse(
      id = statistic.tableId,
      status = statistic.status,
      stats = statistic.columnsStatistics.map { columnStatistics =>
        TableColumnStatisticsResponse.fromDomain(columnStatistics.columnName, columnStatistics.columnsStatistics)
      }
    )

}
