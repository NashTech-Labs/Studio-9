package sqlserver.dao.table

import java.sql.Types._
import java.sql.{ Connection, PreparedStatement, ResultSet, ResultSetMetaData }

import akka.NotUsed
import akka.stream.scaladsl.Source
import cats.data.EitherT
import cats.implicits._
import net.sf.jsqlparser.statement.select.Select
import net.sf.jsqlparser.util.deparser.{ SelectDeParser, StatementDeParser }
import sqlserver.dao.table.RedshiftTableDataDao.SubstitutionResult
import sqlserver.dao.table.TableDataDao.TableDataDaoError
import sqlserver.domain.table.DBValue.{ DBBooleanValue, DBDoubleValue, DBIntValue, DBStringValue }
import sqlserver.domain.table.TableRowValue._
import sqlserver.domain.table._
import sqlserver.services.db.connectionpool.ConnectionProvider
import sqlserver.utils.TryExtensions._

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

class RedshiftTableDataDao(
  connectionProvider: ConnectionProvider,
  fetchSize: Int
) extends TableDataDao {

  override def getTableRowSource(
    query: Select,
    bindings: Map[String, DBValue]
  )(implicit ec: ExecutionContext): Future[Either[TableDataDaoError, TableQueryResult]] = {
    val result = for {
      substitutionResult <- EitherT(substituteBindings(query, bindings).toFuture)
      source <- EitherT(getQueryResult(substitutionResult.modifiedQuery, substitutionResult.bindings))
    } yield source

    result.value
  }

  private def getQueryResult(
    query: String,
    bindings: Seq[DBValue]
  )(implicit ec: ExecutionContext): Future[Either[TableDataDaoError, TableQueryResult]] = {

    def getResultColumns(metadata: ResultSetMetaData): Seq[Column] =
      for {
        c <- 1 to metadata.getColumnCount
        columnName = metadata.getColumnName(c)
        columnDataType = metadata.getColumnType(c) match {
          case TINYINT | SMALLINT | INTEGER => ColumnDataType.Integer
          case BIGINT => ColumnDataType.Long
          case BOOLEAN => ColumnDataType.Boolean
          case NUMERIC | DECIMAL | FLOAT | REAL | DOUBLE => ColumnDataType.Double
          case TIMESTAMP => ColumnDataType.Timestamp
          case _ => ColumnDataType.String
        }
      } yield Column(columnName, columnDataType)

    def executeQuery(statement: PreparedStatement): Future[Either[TableDataDaoError, ResultSet]] =
      Future(statement.executeQuery().asRight).recover {
        case throwable => TableDataDaoError.EngineError(throwable).asLeft
      }

    def buildSource(
      resultSet: ResultSet,
      columns: Seq[Column],
      statement: PreparedStatement
    ): Source[TableRow, NotUsed] =
      Source.unfold(()) { _ =>
        if (resultSet.next()) {
          Some(((), parseTableRow(resultSet)(columns)))
        } else {
          resultSet.close()
          statement.close()
          None
        }
      }

    val result = for {
      connection <- EitherT.right[TableDataDaoError](connectionProvider.getConnection.toFuture)
      statement <- EitherT.right[TableDataDaoError](prepareStatement(connection, query, bindings).toFuture)
      resultSet <- EitherT(executeQuery(statement))
      metaData = resultSet.getMetaData
      columns = getResultColumns(metaData)
      source = buildSource(resultSet, columns, statement)
    } yield TableQueryResult(source, columns, None)

    result.value
  }

  private[table] def substituteBindings(
    query: Select,
    bindings: Map[String, DBValue]
  ): Try[Either[TableDataDaoError.ParameterNotFound, SubstitutionResult]] =
    Try {
      val buffer = new java.lang.StringBuilder()
      val bindingsReplacer = new RedshiftBindingsReplacer(buffer, bindings)
      val selectDeparser = new SelectDeParser(bindingsReplacer, buffer)
      val statementDeparser = new StatementDeParser(bindingsReplacer, selectDeparser, buffer)
      query.accept(statementDeparser)
      SubstitutionResult(
        buffer.toString,
        bindingsReplacer.resultBindingValues
      ).asRight[TableDataDaoError.ParameterNotFound]
    } recover {
      case ParameterNotFoundException(parameterName) =>
        TableDataDaoError.ParameterNotFound(parameterName).asLeft
    }

  private def parseTableRow(results: ResultSet)(columns: Seq[Column]): TableRow = {

    def convertToValue[T](primitive: T)(wrap: T => TableRowValue): TableRowValue =
      if (primitive == null) NullValue
      else wrap(primitive)

    val rowValues = columns.zip(Stream from 1).map {
      case (column, index) =>
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

  private[table] def prepareStatement(
    connection: Connection,
    query: String,
    bindings: Seq[DBValue]
  ): Try[PreparedStatement] = Try {
    val statement = connection.prepareStatement(query, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)
    statement.setFetchSize(Math.max(1, fetchSize))

    bindings.zip(Stream from 1).foreach {
      case (binding, index) =>
        binding match {
          case DBBooleanValue(value) => statement.setBoolean(index, value)
          case DBDoubleValue(value) => statement.setDouble(index, value)
          case DBIntValue(value) => statement.setInt(index, value)
          case DBStringValue(value) => statement.setString(index, value)
        }
    }
    statement
  }
}

object RedshiftTableDataDao {

  private[table] case class SubstitutionResult(modifiedQuery: String, bindings: List[DBValue])

}
