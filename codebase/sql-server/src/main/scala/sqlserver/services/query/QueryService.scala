package sqlserver.services.query

import cats.data.EitherT
import cats.implicits._
import net.sf.jsqlparser.parser.CCJSqlParserUtil
import net.sf.jsqlparser.statement.select.Select
import net.sf.jsqlparser.util.TablesNamesFinder
import sqlserver.dao.table.TableDataDao
import sqlserver.dao.table.TableDataDao.TableDataDaoError
import sqlserver.domain.table.{ DBValue, Table, TableQueryResult }
import sqlserver.services.baile.BaileService
import sqlserver.services.baile.BaileService.DereferenceTablesError
import sqlserver.services.query.QueryService.ExecuteError
import sqlserver.services.query.QueryService.ExecuteError.{
  AccessDenied,
  EngineError,
  InvalidSql,
  ParameterNotFound,
  SqlIsNotSelect,
  TableNotFound,
  Unauthenticated
}
import sqlserver.services.usermanagement.UMService
import sqlserver.services.usermanagement.UMService.TokenValidationError

import scala.collection.JavaConverters._
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }

class QueryService(
  dao: TableDataDao,
  umService: UMService,
  baileService: BaileService
)(implicit ec: ExecutionContext) {

  def execute(
    userToken: String,
    query: String,
    tables: Map[String, String],
    bindings: Map[String, DBValue]
  ): Future[Either[ExecuteError, TableQueryResult]] = {

    def parseQuery(): Either[ExecuteError, Select] =
      Try(CCJSqlParserUtil.parse(query)) match {
        case Failure(exception) =>
          val errorMessage = Option(exception.getCause) match {
            case Some(cause) => cause.getMessage
            case None => exception.getMessage
          }
          InvalidSql(errorMessage).asLeft
        case Success(select: Select) => select.asRight
        case Success(_) => SqlIsNotSelect.asLeft
      }

    def findTableIds(tableAliases: List[String]): Either[ExecuteError, List[String]] = {
      type EitherE[R] = Either[ExecuteError, R]
      tableAliases.traverse[EitherE, String] { tableAlias =>
        Either.fromOption(tables.get(tableAlias), ExecuteError.TableIdNotProvided(tableAlias))
      }
    }

    val result = for {
      user <- EitherT(umService.validateAccessToken(userToken)).leftMap {
        case TokenValidationError.InvalidToken => Unauthenticated
      }
      select <- EitherT.fromEither[Future](parseQuery())
      tablesNamesFinder = new TablesNamesFinder
      tableAliases = tablesNamesFinder.getTableList(select).asScala.toList
      tableIds <- EitherT.fromEither[Future](findTableIds(tableAliases))
      tableReferenceResponses <- EitherT(baileService.dereferenceTables(user.id, tableIds)).leftMap {
        case DereferenceTablesError.AccessDenied => AccessDenied
        case DereferenceTablesError.TableNotFound(tableId) => TableNotFound(tableId)
      }
      actualTables = tableReferenceResponses.map { tableReferenceResponse =>
        Table(
          schema = tableReferenceResponse.schema,
          name = tableReferenceResponse.tableName
        )
      }
      tableNamesMap = tableAliases.zip(actualTables).toMap
      tableRenamer = new TableRenameVisitor(tableNamesMap)
      _ = tableRenamer.visit(select)
      queryResult <- EitherT(dao.getTableRowSource(select, bindings)).leftMap[ExecuteError] {
        case TableDataDaoError.ParameterNotFound(key) => ParameterNotFound(key)
        case TableDataDaoError.EngineError(throwable) => EngineError(throwable)
      }
    } yield queryResult

    result.value
  }

}

object QueryService {

  sealed trait ExecuteError

  object ExecuteError {
    case object SqlIsNotSelect extends ExecuteError
    case class InvalidSql(errorInfo: String) extends ExecuteError
    case class TableIdNotProvided(tableAlias: String) extends ExecuteError
    case class EngineError(throwable: Throwable) extends ExecuteError
    case class ParameterNotFound(key: String) extends ExecuteError
    case object Unauthenticated extends ExecuteError
    case object AccessDenied extends ExecuteError
    case class TableNotFound(tableId: String) extends ExecuteError
  }

}
