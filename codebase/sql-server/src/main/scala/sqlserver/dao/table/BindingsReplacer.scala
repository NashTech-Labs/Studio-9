package sqlserver.dao.table

import net.sf.jsqlparser.expression.JdbcNamedParameter
import net.sf.jsqlparser.util.deparser.ExpressionDeParser
import sqlserver.domain.table.DBValue

class BindingsReplacer(buffer: java.lang.StringBuilder, bindings: Map[String, DBValue]) extends ExpressionDeParser {

  override def visit(parameter: JdbcNamedParameter): Unit =
    bindings.get(parameter.getName) match {
      case Some(value) =>
        value match {
          case DBValue.DBStringValue(str) =>
            val resultStr = str.replace("'", "''")
            buffer.append(s"'$resultStr'")
          case DBValue.DBIntValue(int) =>
            buffer.append(int)
          case DBValue.DBDoubleValue(dbl) =>
            buffer.append(dbl)
          case DBValue.DBBooleanValue(bool) =>
            buffer.append(bool)
        }
      case None =>
        throw ParameterNotFoundException(parameter.getName)
    }

}
