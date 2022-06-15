package sqlserver.dao.table

import net.sf.jsqlparser.expression.JdbcNamedParameter
import net.sf.jsqlparser.util.deparser.ExpressionDeParser
import sqlserver.domain.table.DBValue

class RedshiftBindingsReplacer(buffer: java.lang.StringBuilder, bindings: Map[String, DBValue])
    extends ExpressionDeParser {

  private var _resultBindingsValues: List[DBValue] = List.empty

  override def visit(parameter: JdbcNamedParameter): Unit =
    bindings.get(parameter.getName) match {
      case Some(value) =>
        buffer.append("?")
        _resultBindingsValues ::= value
      case None =>
        throw ParameterNotFoundException(parameter.getName)
    }

  def resultBindingValues: List[DBValue] = _resultBindingsValues.reverse

}
