package sqlserver.routes.query

import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.alpakka.csv.scaladsl.CsvFormatting
import play.api.libs.json.Json
import sqlserver.domain.table
import sqlserver.domain.table.TableRowValue
import sqlserver.routes.BaseRoutes
import sqlserver.routes.contract.common.ErrorResponse
import sqlserver.routes.contract.query._
import sqlserver.services.query.QueryService
import sqlserver.services.query.QueryService.ExecuteError

import scala.collection.immutable

class QueryRoutes(service: QueryService) extends BaseRoutes {

  val routes: Route = path("query") {
    (post & entity(as[QueryRequest])) { request =>
      val bindings: Map[String, table.DBValue] = request.bindings.getOrElse(Map.empty[String, DBValue]).map {
        case (k, v) => k -> DBValue.toDomain(v)
      }

      val result = service.execute(request.token, request.query, request.tables, bindings)

      onSuccess(result) {
        case Right(queryResult) =>
          val columnInfo: String = Json.toJson(queryResult.columnsInfo.map(ColumnResponse.fromDomain)).toString()
          val rawHeaders: immutable.Seq[HttpHeader] = queryResult.rowCount match {
            case Some(count) =>
              immutable.Seq(
                headers.RawHeader("X-SQL-Columns", columnInfo),
                headers.RawHeader("X-SQL-Rows-Count", count.toString)
              )
            case None =>
              immutable.Seq(
                headers.RawHeader("X-SQL-Columns", columnInfo)
              )
          }

          def tableRowValueToString(tableRowValue: TableRowValue): String = tableRowValue match {
            case TableRowValue.StringValue(value) => value
            case TableRowValue.BooleanValue(value) => value.toString
            case TableRowValue.IntegerValue(value) => value.toString
            case TableRowValue.DoubleValue(value) => value.toString
            case TableRowValue.LongValue(value) => value.toString
            case TableRowValue.TimestampValue(value) => value
            case TableRowValue.NullValue => "null" // FIXME how do we distinguish between null string and null value?
          }

          val source = queryResult.source
            .map { row =>
              row.values.map(tableRowValueToString).toList
            }
            .via(CsvFormatting.format())

          respondWithHeaders(rawHeaders) {
            complete(HttpEntity(ContentTypes.`text/csv(UTF-8)`, source))
          }
        case Left(error) => complete(translateError(error))
      }
    }
  }

  def translateError(error: ExecuteError): (StatusCode, ErrorResponse) = error match {
    case ExecuteError.SqlIsNotSelect =>
      errorResponse(StatusCodes.BadRequest, "SQL is not a select statement")
    case ExecuteError.InvalidSql(errorInfo) =>
      errorResponse(StatusCodes.BadRequest, s"Invalid SQL : $errorInfo")
    case ExecuteError.TableIdNotProvided(tableAlias) =>
      errorResponse(StatusCodes.BadRequest, s"Id for table $tableAlias not provided")
    case ExecuteError.ParameterNotFound(key) =>
      errorResponse(StatusCodes.BadRequest, s"Parameter not found : $key")
    case ExecuteError.EngineError(error) =>
      errorResponse(StatusCodes.BadRequest, s"SQL execution error: ${error.getMessage}")
    case ExecuteError.Unauthenticated =>
      errorResponse(StatusCodes.Unauthorized, "Authentication Failed")
    case ExecuteError.AccessDenied =>
      errorResponse(StatusCodes.Unauthorized, "Access Denied")
    case ExecuteError.TableNotFound(tableId) =>
      errorResponse(StatusCodes.NotFound, s"Table not found $tableId")
  }

}
