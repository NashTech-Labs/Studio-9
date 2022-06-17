package sqlserver.services.query

import net.sf.jsqlparser.expression.ExpressionVisitorAdapter
import net.sf.jsqlparser.schema.{ Column, Table }
import net.sf.jsqlparser.statement.StatementVisitorAdapter
import net.sf.jsqlparser.statement.select.{
  FromItemVisitorAdapter,
  PlainSelect,
  Select,
  SelectExpressionItem,
  SelectItemVisitorAdapter,
  SelectVisitorAdapter
}
import sqlserver.domain.table.{ Table => DomainTable }

class TableRenameVisitor(tables: Map[String, DomainTable]) extends StatementVisitorAdapter {

  private val fromItemVisitor = new FromItemVisitorAdapter {
    override def visit(table: Table): Unit = renameTable(table)
  }

  private val columnVisitor = new ExpressionVisitorAdapter {
    override def visit(column: Column): Unit = visitNullable(column.getTable)(renameTable)
  }

  override def visit(select: Select): Unit =
    select.getSelectBody.accept(new SelectVisitorAdapter {
      override def visit(plainSelect: PlainSelect): Unit = {
        visitNullable(plainSelect.getSelectItems)(_.forEach(_.accept(new SelectItemVisitorAdapter {
          override def visit(item: SelectExpressionItem): Unit = item.getExpression.accept(columnVisitor)
        })))
        visitNullable(plainSelect.getFromItem)(_.accept(fromItemVisitor))
        visitNullable(plainSelect.getJoins)(_.forEach { join =>
          join.getRightItem.accept(fromItemVisitor)
          Option(join.getOnExpression).foreach(_.accept(columnVisitor))
        })
        visitNullable(plainSelect.getWhere)(_.accept(columnVisitor))
      }
    })

  private def renameTable(table: Table): Unit =
    tables.get(table.getName).foreach { dTable =>
      table.setSchemaName(dTable.schema)
      table.setName(dTable.name)
    }

  private def visitNullable[T](x: T)(f: T => Any): Unit =
    Option(x).foreach(f)
}
