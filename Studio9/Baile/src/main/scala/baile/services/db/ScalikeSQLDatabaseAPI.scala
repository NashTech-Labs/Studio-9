package baile.services.db

import java.sql.{ Connection, ResultSet }

import akka.NotUsed
import akka.event.LoggingAdapter
import akka.stream.scaladsl.Source
import baile.domain.table.TableRowValue._
import baile.domain.table.{ Column, ColumnDataType, TableRow, TableRowValue }
import baile.services.db.connectionpool.ConnectionProvider
import baile.utils.TryExtensions._
import com.typesafe.config.Config
import scalikejdbc.{ DB, SQL }

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

class ScalikeSQLDatabaseAPI(
  connectionProvider: ConnectionProvider,
  conf: Config
)(
  implicit val logger: LoggingAdapter
) extends SQLDatabaseAPI[ResultSet] {

  private val fetchSize = conf.getInt("fetch-size")

  override def execute[T](
    query: String,
    binds: Seq[String] = Seq.empty[String]
  )(resultParser: ResultSet => Try[T])(implicit ec: ExecutionContext): Future[Seq[T]] =
    for {
      connection <- connectionProvider.getConnection.toFuture
      tryResults <- Future {
        // TODO get rid of scalikeJDBC. We're only using it in this block, the rest is done with pure JDBC.
        DB(connection) readOnly { implicit session =>
          logger.debug(s"Executing SQL: $query")

          val sql = if (binds.nonEmpty) SQL(query).bind(binds: _*) else SQL(query)

          sql.map(wrappedResultSet => resultParser(wrappedResultSet.underlying)).list.apply()
        }
      }
      results <- Try.sequence(tryResults).toFuture
    } yield results

  override def getTableSource(
    query: String,
    columns: Seq[Column]
  )(implicit ec: ExecutionContext): Future[Source[TableRow, NotUsed]] = {

    def createSource(connection: Connection): Future[Source[TableRow, NotUsed]] = Future {
      val statement = connection.prepareStatement(query, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)
      statement.setFetchSize(Math.max(1, fetchSize / columns.size))

      val results = statement.executeQuery()

      Source.unfold(()) { _ =>
        if (results.next()) {
          Some(((), parseTableRow(columns)(results).get))
        } else {
          results.close()
          statement.close()
          None
        }
      }
    }

    for {
      connection <- connectionProvider.getConnection.toFuture
      source <- createSource(connection)
    } yield source
  }

  override def parseTableRow(columns: Seq[Column])(results: ResultSet): Try[TableRow] = Try {

    def convertToValue[T](primitive: T)(wrap: T => TableRowValue): TableRowValue =
      if (primitive == null) NullValue
      else wrap(primitive)

    val rowValues = columns.zipWithIndex.map {
      case (column, _index) =>
        val index = _index + 1
        column.dataType match {
          case ColumnDataType.String => convertToValue(results.getString(index))(StringValue(_))
          case ColumnDataType.Integer => convertToValue(results.getInt(index))(IntegerValue(_))
          case ColumnDataType.Boolean => convertToValue(results.getBoolean(index))(BooleanValue(_))
          case ColumnDataType.Double => convertToValue(results.getDouble(index))(DoubleValue(_))
          case ColumnDataType.Long => convertToValue(results.getLong(index))(LongValue(_))
          case ColumnDataType.Timestamp =>
            convertToValue(results.getTimestamp(index))(ts => TimestampValue(ts.toString))
        }
    }

    TableRow(rowValues)
  }

  override def parseTableRowCount(results: ResultSet): Try[Long] = Try {
    results.getInt(1)
  }

}
