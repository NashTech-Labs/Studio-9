package sqlserver.dao.table

import net.sf.jsqlparser.statement.select.Select
import sqlserver.dao.table.TableDataDao.TableDataDaoError
import sqlserver.domain.table.{ DBValue, TableQueryResult }

import scala.concurrent.{ ExecutionContext, Future }

trait TableDataDao {

  def getTableRowSource(
    query: Select,
    bindings: Map[String, DBValue]
  )(implicit ec: ExecutionContext): Future[Either[TableDataDaoError, TableQueryResult]]

}

object TableDataDao {

  sealed trait TableDataDaoError

  object TableDataDaoError {
    case class ParameterNotFound(key: String) extends TableDataDaoError
    case class EngineError(throwable: Throwable) extends TableDataDaoError
  }

}
