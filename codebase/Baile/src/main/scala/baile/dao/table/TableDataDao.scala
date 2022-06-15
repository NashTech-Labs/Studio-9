package baile.dao.table

import akka.NotUsed
import akka.stream.scaladsl.Source
import baile.daocommons.filters.Filter
import baile.daocommons.sorting.SortBy
import baile.domain.table.{ Table, TableRow, TableRowValue }

import scala.concurrent.{ ExecutionContext, Future }

trait TableDataDao {

  def getRows(
    table: Table,
    pageSize: Int,
    pageNumber: Int,
    search: Option[Filter],
    sortParams: Option[SortBy]
  )(implicit ec: ExecutionContext): Future[Seq[TableRow]]

  def getTableRowSource(table: Table)(implicit ec: ExecutionContext): Future[Source[TableRow, NotUsed]]

  def getColumnValues(
    table: Table,
    columnName: String,
    limit: Int,
    search: Option[Filter]
  )(implicit ec: ExecutionContext): Future[Seq[TableRowValue]]

  def getRowsCount(
    table: Table,
    search: Option[Filter]
  )(implicit ec: ExecutionContext): Future[Long]

}
