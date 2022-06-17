package sqlserver.services.query

import java.util.UUID

import akka.stream.scaladsl.Source
import cortex.api.baile.TableReferenceResponse
import net.sf.jsqlparser.statement.select.Select
import sqlserver.BaseSpec
import sqlserver.RandomGenerators._
import sqlserver.dao.table.TableDataDao
import sqlserver.domain.table.DBValue.{ DBDoubleValue, DBStringValue }
import sqlserver.domain.table.{ Table, TableQueryResult }
import sqlserver.services.baile.BaileService
import sqlserver.services.usermanagement.UMService
import sqlserver.services.usermanagement.datacontract.UserResponse
import cats.implicits._
import sqlserver.services.baile.BaileService.DereferenceTablesError
import sqlserver.services.query.QueryService.ExecuteError

class QueryServiceSpec extends BaseSpec {

  trait Setup {
    val tableDao = mock[TableDataDao]
    val umService = mock[UMService]
    val baileServie = mock[BaileService]

    val service = new QueryService(tableDao, umService, baileServie)

    val token = randomString()

    val user = UserResponse(
      UUID.randomUUID(),
      "john.doe",
      "john.doe@example.com",
      "John",
      "Doe"
    )
  }

  "QueryServiceSpec#execute" should {

    "parse sql statement and return source of values" in new Setup {
      val tables = Map(
        "myTable" -> "1id",
        "myTable2" -> "2id"
      )
      val tableLocations = Map(
        "1id" -> Table("schema1", "actualTableNameFoo"),
        "2id" -> Table("schema2", "actualTableNameBar")
      )
      val tableQueryResult = TableQueryResult(Source.empty, Seq.empty, None)

      val inputQuery =
        "SELECT col1, col2, myTable.col3, t1.col1 FROM" +
          " myTable, myTable2 t2 WHERE myTable.col1 = :param1 AND myTable2.col2 >= :param1"

      val expectedSelect =
        "SELECT col1, col2, schema1.actualTableNameFoo.col3, t1.col1 FROM" +
          " schema1.actualTableNameFoo, schema2.actualTableNameBar t2" +
          " WHERE schema1.actualTableNameFoo.col1 = :param1 AND schema2.actualTableNameBar.col2 >= :param1"

      val bindings = Map(
        "param1" -> DBStringValue("quix"),
        "param2" -> DBDoubleValue(42.2)
      )

      umService.validateAccessToken(token) shouldReturn future(user.asRight)
      baileServie.dereferenceTables(user.id, tableIds = tableLocations.keys.toList) shouldReturn future(
        tableLocations.values.map { case Table(schema, name) => TableReferenceResponse(name, schema) }.toList.asRight
      )
      tableDao.getTableRowSource(
        argThat[Select] { select: Select =>
          select.toString == expectedSelect
        },
        bindings
      ) shouldReturn future(tableQueryResult.asRight)
      service.execute(
        token,
        inputQuery,
        tables,
        bindings
      )
    }

    "return an error if there is unknown table alias in the query" in new Setup {
      val tables = Map(
        "myTable2" -> "2id"
      )
      val inputQuery =
        "SELECT col1, col2, myTable.col3, t1.col1 FROM myTable, myTable2"

      umService.validateAccessToken(token) shouldReturn future(user.asRight)

      whenReady(service.execute(
        token,
        inputQuery,
        tables,
        Map.empty
      ))(_ shouldBe ExecuteError.TableIdNotProvided("myTable").asLeft)
    }

    "return an error if a table doesn't exist" in new Setup {
      val tables = Map(
        "myTable" -> "1id"
      )
      val tableLocations = Map(
        "1id" -> Table("schema1", "actualTableNameFoo")
      )

      val inputQuery =
        "SELECT * FROM myTable"

      umService.validateAccessToken(token) shouldReturn future(user.asRight)
      baileServie.dereferenceTables(user.id, tableIds = tableLocations.keys.toList) shouldReturn future(
        DereferenceTablesError.TableNotFound("1id").asLeft
      )

      whenReady(service.execute(
        token,
        inputQuery,
        tables,
        Map.empty
      ))(_ shouldBe ExecuteError.TableNotFound("1id").asLeft)
    }
  }
}
