package baile.dao.table

import akka.NotUsed
import akka.stream.scaladsl.Source
import baile.daocommons.filters._
import baile.daocommons.sorting.{ Direction, SortBy }
import baile.domain.table.{ Column, Table, TableRow, TableRowValue }
import baile.dao.table.SQLTableDataDao._
import baile.utils.TryExtensions._
import cats.implicits._

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }

trait SQLTableDataDao[RS] extends TableDataDao {

  override def getRows(
    table: Table,
    pageSize: Int,
    pageNumber: Int,
    search: Option[Filter],
    sortParams: Option[SortBy]
  )(implicit ec: ExecutionContext): Future[Seq[TableRow]] = {

    def buildSql(
      columnsSelector: String,
      predicate: Option[String],
      fullTableName: String,
      orderBy: Option[String],
      offset: Int,
      limit: Int
    ): String =
      s"SELECT $columnsSelector FROM $fullTableName" +
        predicate.fold("")(" WHERE " + _) +
        orderBy.fold("")(" ORDER BY " + _) +
        s" LIMIT $limit OFFSET $offset"

    val offset = pageSize * (pageNumber - 1)

    executeOnTable(table) { fullTableName =>
      for {
        predicate <- search.traverse(buildPredicate).toFuture
        orderBy <- sortParams.traverse(prepareOrderByParam).toFuture
        columnsSelector <- buildColumnsSelector(table.columns).toFuture
        sql = buildSql(columnsSelector, predicate, fullTableName, orderBy, offset, pageSize)
        result <- execute(sql)(parseTableRow(table.columns))
      } yield result
    }
  }

  override def getTableRowSource(
    table: Table
  )(implicit ec: ExecutionContext): Future[Source[TableRow, NotUsed]] =
    executeOnTable(table) { fullTableName =>
      for {
        columnsSelector <- buildColumnsSelector(table.columns).toFuture
        sql = s"SELECT $columnsSelector FROM $fullTableName"
        result <- getTableRowSource(sql, table.columns)
      } yield result
    }

  override def getRowsCount(table: Table, search: Option[Filter])(implicit ex: ExecutionContext): Future[Long] =
    executeOnTable(table) { fullTableName =>
      for {
        predicate <- search.traverse(buildPredicate).toFuture
        sql = s"SELECT count(*) FROM $fullTableName" + predicate.fold("")(" WHERE " + _)
        result <- execute(sql)(parseTableRowCount)
      } yield result.headOption.getOrElse(throw new RuntimeException("Unable to find row count."))
    }

  override def getColumnValues(
    table: Table,
    columnName: String,
    limit: Int,
    search: Option[Filter]
  )(implicit ec: ExecutionContext): Future[Seq[TableRowValue]] = {

    def findColumn: Try[Column] = Try {
      table.columns.find(_.name == columnName).getOrElse(
        throw new RuntimeException(s"unable to find column $columnName")
      )
    }

    def buildSql(columnName: String, tableName: String, predicate: Option[String], limit: Int): String = {
      s"SELECT DISTINCT $columnName FROM $tableName" +
        predicate.fold("")(" WHERE " + _) +
        s" ORDER BY $columnName" +
        s" LIMIT $limit"
    }

    executeOnTable(table) { fullTableName =>
      for {
        columnIdentifier <- validateAndQuoteIdentifier(columnName).toFuture
        column <- findColumn.toFuture
        predicate <- search.traverse(buildPredicate).toFuture
        sql = buildSql(columnIdentifier, fullTableName, predicate, limit)
        result <- execute(sql)(parseSingleResult(column))
      } yield result
    }
  }

  protected def execute[T](query: String)(resultParser: RS => Try[T])(implicit ec: ExecutionContext): Future[Seq[T]]

  protected def parseTableRow(columns: Seq[Column])(results: RS): Try[TableRow]

  protected def getTableRowSource(
    query: String,
    columns: Seq[Column]
  )(implicit ec: ExecutionContext): Future[Source[TableRow, NotUsed]]

  protected def parseSingleResult(column: Column): RS => Try[TableRowValue] =
    parseTableRow(Seq(column))_ andThen (_.map(_.values.headOption.get))

  protected final def executeOnTable[T](
    table: Table
  )(f: String => Future[T])(implicit ec: ExecutionContext): Future[T] =
    for {
      schema <- validateAndQuoteIdentifier(table.repositoryId).toFuture
      tableName <- validateAndQuoteIdentifier(table.databaseId).toFuture
      fullTableName = schema + "." + tableName
      result <- f(fullTableName)
    } yield result

  protected def parseTableRowCount(results: RS): Try[Long]

  // Escape apostrophe with double apostrophes and wrap the entire thing with apostrophes
  protected def quoteLiteral(str: String): String = s"'${ str.replace("'", "''") }'"

  // Escape quotes with double quotes and wrap the entire thing with quotes
  protected def validateAndQuoteIdentifier(identifier: String): Try[String] =
    Success("\"" + identifier.replaceAll("\"", "\"\"") + "\"")

  private def prepareOrderByParam(sortParams: SortBy): Try[String] =
    Try.sequence(sortParams.fields.map {
      case (column: Column, direction) =>
        validateAndQuoteIdentifier(column.name).map { columnIdentifier =>
          direction match {
            case Direction.Ascending => columnIdentifier + " ASC"
            case Direction.Descending => columnIdentifier + " DESC"
          }
        }
      case _ =>
        Failure(new RuntimeException("Sort params can only be prepared for Column field"))
    }).map(_.mkString(", "))

  private def buildPredicate(search: Filter): Try[String] = {

    def combineSqlPartsWithBinaryOperator(
      left: Filter,
      right: Filter,
      operator: String
    ): String = s"(${ prepareFilter(left) } $operator ${ prepareFilter(right) }"

    def prepareFilter(filter: Filter): String =
      filter match {
        case TrueFilter => "TRUE"
        case FalseFilter => "FALSE"
        case Not(nested) => s"NOT ${ prepareFilter(nested) }"
        case Or(left, right) => combineSqlPartsWithBinaryOperator(left, right, "OR")
        case And(left, right) => combineSqlPartsWithBinaryOperator(left, right, "AND")
        case EqualTo(column, arg) => s"${ quoteLiteral(column.name) } = ${ quoteLiteral(arg) }"
        case ILike(column, arg) => s" ${ quoteLiteral(column.name) } ILIKE ${ quoteLiteral("%" + arg + "%") }"
        case unsupportedFilter => throw new RuntimeException(s"$unsupportedFilter filter is not supported.")
      }

    Try(prepareFilter(search))
  }

  private def buildColumnsSelector(columns: Seq[Column]): Try[String] =
    Try.sequence(
      columns.map { column =>
        validateAndQuoteIdentifier(column.name)
      }
    ).map(_.mkString(", "))

}

object SQLTableDataDao {

  case class EqualTo(column: Column, argument: String) extends Filter
  case class ILike(column: Column, argument: String) extends Filter

}
