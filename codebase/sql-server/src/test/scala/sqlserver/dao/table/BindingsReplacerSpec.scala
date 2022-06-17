package sqlserver.dao.table

import net.sf.jsqlparser.parser.CCJSqlParserUtil
import net.sf.jsqlparser.util.deparser.{ SelectDeParser, StatementDeParser }
import sqlserver.BaseSpec
import sqlserver.domain.table.DBValue
import sqlserver.domain.table.DBValue.{ DBBooleanValue, DBDoubleValue, DBIntValue, DBStringValue }

class BindingsReplacerSpec extends BaseSpec {

  def test(input: String, bindings: Map[String, DBValue], expectedResult: String): Unit = {
    val buffer = new java.lang.StringBuilder()
    val bindingsReplacer = new BindingsReplacer(buffer, bindings)
    val selectDeparser = new SelectDeParser(bindingsReplacer, buffer)
    val statementDeparser = new StatementDeParser(bindingsReplacer, selectDeparser, buffer)

    val query = CCJSqlParserUtil.parse(input)
    query.accept(statementDeparser)

    val result = buffer.toString
    result shouldBe expectedResult
    CCJSqlParserUtil.parse(result)
  }

  "BindingsReplacer" should {

    "replace simple bindings" in test(
      input = "SELECT * FROM table WHERE " +
        "col1 = :param1 AND " +
        "col2 = :param2 AND " +
        "col3 = :param3 AND " +
        "col4 = :param4 AND " +
        "col5 = :param5 AND " +
        "col6 = :param6 AND " +
        "col7 = :param7 AND " +
        "col8 = :param8",
      bindings = Map(
        "param1" -> DBStringValue("foo"),
        "param2" -> DBStringValue("bar"),
        "param3" -> DBIntValue(42),
        "param4" -> DBIntValue(-12),
        "param5" -> DBDoubleValue(0.5281935172398712),
        "param6" -> DBDoubleValue(2.358912385813E8),
        "param7" -> DBBooleanValue(false),
        "param8" -> DBBooleanValue(true)
      ),
      expectedResult = "SELECT * FROM table WHERE " +
        "col1 = 'foo' AND " +
        "col2 = 'bar' AND " +
        "col3 = 42 AND " +
        "col4 = -12 AND " +
        "col5 = 0.5281935172398712 AND " +
        "col6 = 2.358912385813E8 AND " +
        "col7 = false AND " +
        "col8 = true"
    )

    "replace bindings with sql injection in string literal" in test(
      input = "SELECT * FROM table WHERE x = :param1",
      bindings = Map("param1" -> DBStringValue("'; DROP TABLE table;--")),
      expectedResult = "SELECT * FROM table WHERE x = '''; DROP TABLE table;--'"
    )

  }

}
