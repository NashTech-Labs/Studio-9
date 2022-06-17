package baile.dao.table

import java.sql.{ Connection, PreparedStatement, ResultSet, Timestamp }
import java.time.Instant

import akka.stream.scaladsl.Sink
import baile.ExtendedBaseSpec
import baile.domain.table.TableRowValue.{ BooleanValue, DoubleValue, LongValue, StringValue, TimestampValue }
import baile.domain.table.{ Column, ColumnAlign, ColumnDataType, ColumnVariableType }
import baile.services.db.connectionpool.ConnectionProvider
import baile.utils.TryExtensions._

import scala.util.{ Success, Try }

class RedshiftTableDataDaoSpec extends ExtendedBaseSpec {

  trait Setup {

    val connectionProvider = mock[ConnectionProvider]

    val dao = new RedshiftTableDataDao(
      connectionProvider = connectionProvider,
      fetchSize = 200
    )
  }

  "RedshiftTableDataDao#execute" should {

    "execute given sql and return sequence of results" in new Setup {
      val query = "SELECT * FROM table;"

      val connection = mock[Connection]
      val preparedStatement = mock[PreparedStatement]
      val resultSet = mock[ResultSet]

      connectionProvider.getConnection shouldReturn Success(connection)
      preparedStatement.executeQuery shouldReturn resultSet
      connection.prepareStatement(query) shouldReturn preparedStatement

      resultSet.close() isLenient()
      connection.setAutoCommit(true) isLenient()
      connection.setReadOnly(true) isLenient()
      preparedStatement.close() isLenient()
      connection.close() isLenient()

      resultSet.next() shouldReturn true andThen true andThen false

      whenReady(dao.execute(
        "SELECT * FROM table;"
      )(_ => Success("result")))(_ shouldBe List("result", "result"))
    }

  }

  "RedshiftTableDataDao#parseTableRow" should {

    val columns = List(
      Column("col1", "column1", ColumnDataType.String, ColumnVariableType.Categorical, ColumnAlign.Center, None),
      Column("col2", "column2", ColumnDataType.Boolean, ColumnVariableType.Categorical, ColumnAlign.Center, None),
      Column("col3", "column3", ColumnDataType.Double, ColumnVariableType.Categorical, ColumnAlign.Center, None),
      Column("col4", "column4", ColumnDataType.Long, ColumnVariableType.Categorical, ColumnAlign.Center, None),
      Column("col5", "column5", ColumnDataType.Timestamp, ColumnVariableType.Categorical, ColumnAlign.Center, None)
    )

    "return row" in new Setup {
      val resultSet = mock[ResultSet]
      val recordsCount = 5

      (1 to recordsCount).foreach { n =>
        resultSet.getString(1) shouldReturn s"string #$n"
        resultSet.getBoolean(2) shouldReturn (n % 2 == 0)
        resultSet.getDouble(3) shouldReturn 1.2 * n
        resultSet.getLong(4) shouldReturn 600l * n
        resultSet.getTimestamp(5) shouldReturn Timestamp.from(Instant.now())
      }

      val results = Try.sequence((1 to recordsCount).map { _ =>
        dao.parseTableRow(columns)(resultSet)
      }).success.get
      results.foreach { row =>
        row.values.length shouldBe 5
        row.values(0) shouldBe a[StringValue]
        row.values(1) shouldBe a[BooleanValue]
        row.values(2) shouldBe a[DoubleValue]
        row.values(3) shouldBe a[LongValue]
        row.values(4) shouldBe a[TimestampValue]
      }
    }

  }

  "RedshiftTableDataDao#getTableSource" should {

    "execute given sql and return source of rows" in new Setup {

      val query = "SELECT * FROM table;"

      val connection = mock[Connection]
      val preparedStatement = mock[PreparedStatement]
      val resultSet = mock[ResultSet]
      val recordsCount = 5

      val columns = List(
        Column("col1", "column1", ColumnDataType.String, ColumnVariableType.Categorical, ColumnAlign.Center, None),
        Column("col2", "column2", ColumnDataType.Boolean, ColumnVariableType.Categorical, ColumnAlign.Center, None),
        Column("col3", "column3", ColumnDataType.Double, ColumnVariableType.Categorical, ColumnAlign.Center, None),
        Column("col4", "column4", ColumnDataType.Long, ColumnVariableType.Categorical, ColumnAlign.Center, None),
        Column("col5", "column5", ColumnDataType.Timestamp, ColumnVariableType.Categorical, ColumnAlign.Center, None)
      )

      connectionProvider.getConnection shouldReturn Success(connection)
      preparedStatement.executeQuery shouldReturn resultSet
      connection.prepareStatement(query, *, *) shouldReturn preparedStatement

      resultSet.close() isLenient()
      preparedStatement.setFetchSize(*) isLenient()
      connection.setAutoCommit(true) isLenient()
      preparedStatement.close() isLenient()
      connection.close() isLenient()

      resultSet.next().shouldReturn(true).andThen(true, List.fill(recordsCount - 2)(true) :+ false: _*)

      (1 to recordsCount).foreach { n =>
        resultSet.getString(1) shouldReturn s"string #$n"
        resultSet.getBoolean(2) shouldReturn (n % 2 == 0)
        resultSet.getDouble(3) shouldReturn 1.2 * n
        resultSet.getLong(4) shouldReturn 600l * n
        resultSet.getTimestamp(5) shouldReturn Timestamp.from(Instant.now())
      }

      whenReady(dao.getTableRowSource(query, columns)) { source =>
        val results = source.runWith(Sink.seq).futureValue
        results.length shouldBe recordsCount
        results.foreach { row =>
          row.values(0) shouldBe a[StringValue]
          row.values(1) shouldBe a[BooleanValue]
          row.values(2) shouldBe a[DoubleValue]
          row.values(3) shouldBe a[LongValue]
          row.values(4) shouldBe a[TimestampValue]
        }
      }
    }

  }

  "RedshiftTableDataDao#parseTableRowCount" should {

    "return rows count from result set" in new Setup {
      val count = 2048
      val resultSet = mock[ResultSet]

      resultSet.getInt(1) shouldReturn count

      dao.parseTableRowCount(resultSet).success.get shouldBe count
    }

  }

}
