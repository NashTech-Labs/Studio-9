package sqlserver.services.query

import net.sf.jsqlparser.parser.CCJSqlParserUtil
import net.sf.jsqlparser.statement.select.Select
import org.scalatest.Assertion
import sqlserver.BaseSpec
import sqlserver.domain.table.Table

class TableRenameVisitorSpec extends BaseSpec {

  def test(input: String, tables: Map[String, Table], expectedOutput: String): Assertion = {
    val tableRenamer = new TableRenameVisitor(tables)
    val select = CCJSqlParserUtil.parse(input).asInstanceOf[Select]
    tableRenamer.visit(select)
    select.toString shouldBe expectedOutput
  }

  "TableRenameVisitor" should {

    "rename all tables correctly for query with no where" in test(
      input = "SELECT * FROM table",
      tables = Map(
        "table" -> Table("user1", "raptors")
      ),
      expectedOutput = "SELECT * FROM user1.raptors"
    )

    "rename all tables correctly for query with no from" in test(
      input = "SELECT 23",
      tables = Map.empty,
      expectedOutput = "SELECT 23"
    )

    "rename all tables correctly for query with where" in test(
      input = "SELECT * FROM myTable1, hisTable2, myTable3" +
        " WHERE myTable1.c1 = 3 AND hisTable2.c1 <> 'foo' OR myTable3.c5 >= 0.4",
      tables = Map(
        "myTable1" -> Table("user1", "laptops"),
        "myTable3" -> Table("user1", "organizations"),
        "hisTable2" -> Table("user2", "cars")
      ),
      expectedOutput = "SELECT * FROM user1.laptops, user2.cars, user1.organizations" +
        " WHERE user1.laptops.c1 = 3 AND user2.cars.c1 <> 'foo' OR user1.organizations.c5 >= 0.4"
    )

    "rename all tables correctly for query with join" in test(
      input = "SELECT * FROM myTable1 AS t1, myTable2 AS t2," +
        " hisTable3 JOIN hisTable4 ON hisTable3.pk = hisTable4.fk" +
        " WHERE t1.price <> 42 AND t2.groupNumber = t1.groupNumber OR hisTable3.count >= hisTable4.count LIMIT 10",
      tables = Map(
        "myTable1" -> Table("user1", "orders"),
        "hisTable3" -> Table("user2", "items"),
        "hisTable4" -> Table("user2", "lines"),
        "myTable2" -> Table("user1", "clients")
      ),
      expectedOutput = "SELECT * FROM user1.orders AS t1, user1.clients AS t2," +
        " user2.items JOIN user2.lines ON user2.items.pk = user2.lines.fk" +
        " WHERE t1.price <> 42 AND t2.groupNumber = t1.groupNumber OR user2.items.count >= user2.lines.count LIMIT 10"
    )

  }

}
