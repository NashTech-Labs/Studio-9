package baile.services.db

import akka.NotUsed
import akka.stream.scaladsl.Source
import baile.domain.table.{ Column, TableRow, TableRowValue }

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

trait SQLDatabaseAPI[RS] {

  def execute[T](
    query: String,
    binds: Seq[String] = Seq.empty[String]
  )(rs: RS => Try[T])(implicit ec: ExecutionContext): Future[Seq[T]]

  def parseTableRow(columns: Seq[Column])(results: RS): Try[TableRow]

  def parseSingleResult(column: Column): RS => Try[TableRowValue] =
    parseTableRow(Seq(column))_ andThen (_.map(_.values.headOption.get))

  def parseTableRowCount(results: RS): Try[Long]

  def getTableSource(
    query: String,
    columns: Seq[Column]
  )(implicit ec: ExecutionContext): Future[Source[TableRow, NotUsed]]

}
