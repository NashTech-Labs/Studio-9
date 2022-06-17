package sqlserver.dao.table

import java.sql._
import java.time.Instant

import akka.stream.scaladsl.Sink
import cats.implicits._
import sqlserver.RandomGenerators._
import net.sf.jsqlparser.parser.CCJSqlParserUtil
import net.sf.jsqlparser.statement.select.Select
import sqlserver.dao.table.RedshiftTableDataDao.SubstitutionResult
import sqlserver.dao.table.TableDataDao.TableDataDaoError.ParameterNotFound
import sqlserver.domain.table.DBValue.{ DBBooleanValue, DBDoubleValue, DBIntValue, DBStringValue }
import sqlserver.domain.table.TableRowValue._
import sqlserver.domain.table.{ Column, ColumnDataType, TableRow }
import sqlserver.services.db.connectionpool.ConnectionProvider
import sqlserver.{ BaseSpec, RandomGenerators }

import scala.util.Try

class RedshiftTableDataDaoSpec extends BaseSpec {

  trait Setup {
    val connectionProvider: ConnectionProvider = mock[ConnectionProvider]
    val resultSet: ResultSet = mock[ResultSet]
    val connection: Connection = mock[Connection]
    val preparedStatement: PreparedStatement = mock[PreparedStatement]
    val resultMetadata: ResultSetMetaData = mock[ResultSetMetaData]
    val fetchSize: Int = RandomGenerators.randomInt(100)
    val dao = new RedshiftTableDataDao(connectionProvider, fetchSize)
  }

  "RedshiftTableDataDao#getTableSource" should {

    trait GetSourceSetup extends Setup {
      connectionProvider.getConnection shouldReturn Try(connection)
      connection.prepareStatement(*, *, *) shouldReturn preparedStatement
    }

    // TODO Add tests on source content (this is very weak test right now)
    "get empty table source" in new GetSourceSetup {

      preparedStatement.executeQuery() shouldReturn resultSet
      preparedStatement.setFetchSize(*) isLenient ()
      preparedStatement.setString(*, *) isLenient ()
      preparedStatement.close() isLenient ()

      resultSet.getMetaData shouldReturn resultMetadata
      resultSet.next() shouldReturn false
      resultSet.close() isLenient ()
      resultMetadata.getColumnCount shouldReturn 1
      resultMetadata.getColumnType(*) shouldReturn Types.VARCHAR
      resultMetadata.getColumnName(*) shouldReturn "column-name"

      val query = "select * from table where column-name = :param"
      whenReady(
        dao.getTableRowSource(
          CCJSqlParserUtil.parse(query).asInstanceOf[Select],
          Map("param" -> DBStringValue(RandomGenerators.randomString()))
        )
      ) { result =>
        result.map(_.columnsInfo) == Right(Seq(Column("column-name", ColumnDataType.String)))
        whenReady(result.right.get.source.runWith(Sink.seq[TableRow]))(_ shouldBe List())
      }
    }

    "build table source by parsing table row" in new GetSourceSetup {

      val recordsCount = 5
      preparedStatement.executeQuery() shouldReturn resultSet
      preparedStatement.setFetchSize(*) isLenient ()
      preparedStatement.setString(*, *) isLenient ()
      preparedStatement.close() isLenient ()

      resultSet.getMetaData shouldReturn resultMetadata
      resultSet.close() isLenient ()
      resultMetadata.getColumnCount shouldReturn 7
      resultMetadata.getColumnType(1) shouldReturn Types.VARCHAR
      resultMetadata.getColumnName(1) shouldReturn "column1"
      resultMetadata.getColumnType(2) shouldReturn Types.BOOLEAN
      resultMetadata.getColumnName(2) shouldReturn "column2"
      resultMetadata.getColumnType(3) shouldReturn Types.DOUBLE
      resultMetadata.getColumnName(3) shouldReturn "column3"
      resultMetadata.getColumnType(4) shouldReturn Types.BIGINT
      resultMetadata.getColumnName(4) shouldReturn "column4"
      resultMetadata.getColumnType(5) shouldReturn Types.TINYINT
      resultMetadata.getColumnName(5) shouldReturn "column5"
      resultMetadata.getColumnType(6) shouldReturn Types.TIMESTAMP
      resultMetadata.getColumnName(6) shouldReturn "column6"
      resultMetadata.getColumnType(7) shouldReturn Types.STRUCT
      resultMetadata.getColumnName(7) shouldReturn "column7"

      resultSet.next().shouldReturn(true).andThen(true, List.fill(recordsCount - 2)(true) :+ false: _*)

      resultSet.getString(1) shouldReturn randomString()
      resultSet.getBoolean(2) shouldReturn randomBoolean()
      resultSet.getDouble(3) shouldReturn 1.2
      resultSet.getLong(4) shouldReturn 600l
      resultSet.getInt(5) shouldReturn randomInt(10)
      resultSet.getTimestamp(6) shouldReturn Timestamp.from(Instant.now())
      resultSet.getString(7) shouldReturn null // scalastyle:off null


      val query = "select * from table where column-name = :param"
      whenReady(
        dao.getTableRowSource(
          CCJSqlParserUtil.parse(query).asInstanceOf[Select],
          Map("param" -> DBStringValue(RandomGenerators.randomString()))
        )
      )(result => {
        result.isRight shouldBe true
        val results = result.right.get.source.runWith(Sink.seq[TableRow]).futureValue
        results.length shouldBe recordsCount
        results.foreach { row =>
          row.values(0) shouldBe a[StringValue]
          row.values(1) shouldBe a[BooleanValue]
          row.values(2) shouldBe a[DoubleValue]
          row.values(3) shouldBe a[LongValue]
          row.values(4) shouldBe a[IntegerValue]
          row.values(5) shouldBe a[TimestampValue]
          row.values(6) shouldBe NullValue
        }
      })
    }

  }

  "RedshiftTableDataDao#substituteBindings" should {

    "substitute all provided bindings in simple query" in new Setup {
      val query = "SELECT * FROM table WHERE col1 = :param1 AND col4 >= :param4 AND col3 <= :param3 AND col2 <> :param2"
      val bindings = Map(
        "param1" -> DBStringValue("foo"),
        "param2" -> DBBooleanValue(false),
        "param3" -> DBDoubleValue(42.2),
        "param4" -> DBIntValue(7)
      )
      val select = CCJSqlParserUtil.parse(query).asInstanceOf[Select]
      val result = dao.substituteBindings(select, bindings)
      result.get shouldBe SubstitutionResult(
        modifiedQuery = "SELECT * FROM table WHERE col1 = ? AND col4 >= ? AND col3 <= ? AND col2 <> ?",
        bindings = List(DBStringValue("foo"), DBIntValue(7), DBDoubleValue(42.2), DBBooleanValue(false))
      ).asRight
    }

    "substitute all provided bindings in more complicated query" in new Setup {
      val query = "SELECT CASE WHEN col > :value THEN True ELSE False END FROM table " +
        "WHERE col1 < (SELECT col3 - :threshold FROM table2 WHERE col3 >= :threshold)"
      val bindings = Map(
        "value" -> DBStringValue("foo"),
        "threshold" -> DBDoubleValue(42.2)
      )
      val select = CCJSqlParserUtil.parse(query).asInstanceOf[Select]
      val result = dao.substituteBindings(select, bindings)
      result.get shouldBe SubstitutionResult(
        modifiedQuery = "SELECT CASE WHEN col > ? THEN True ELSE False END FROM table " +
          "WHERE col1 < (SELECT col3 - ? FROM table2 WHERE col3 >= ?)",
        bindings = List(DBStringValue("foo"), DBDoubleValue(42.2), DBDoubleValue(42.2))
      ).asRight
    }

    "return error when parameter is not provided" in new Setup {
      val query = "SELECT * FROM table WHERE col1 = :param1"
      val select = CCJSqlParserUtil.parse(query).asInstanceOf[Select]
      val result = dao.substituteBindings(select, Map("param42" -> DBIntValue(42)))
      result.get shouldBe ParameterNotFound("param1").asLeft
    }

  }

  "RedshiftTableDataDao#prepareStatement" should {

    "prepare statement by specifying bindings properly" in new Setup {
      connection.prepareStatement(*, *, *) shouldReturn preparedStatement
      preparedStatement.setFetchSize(*) isLenient ()

      val query = "SELECT * FROM table WHERE col1 = :param1 AND col4 >= :param4 AND col3 <= :param3 AND col2 <> :param2"
      val bindings = List(
        DBStringValue("foo"),
        DBIntValue(7),
        DBDoubleValue(42.2),
        DBBooleanValue(false)
      )

      dao.prepareStatement(connection, query, bindings)

      preparedStatement.setString(1, "foo") wasCalled once
      preparedStatement.setInt(2, 7) wasCalled once
      preparedStatement.setDouble(3, 42.2) wasCalled once
      preparedStatement.setBoolean(4, false) wasCalled once
    }

  }
}
