package baile.routes.internal

import java.util.UUID

import akka.http.scaladsl.model.{ StatusCode, StatusCodes }
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.unmarshalling.PredefinedFromStringUnmarshallers.CsvSeq
import baile.routes.BaseRoutes
import baile.services.table.TableService
import baile.services.table.TableService.InternalTableServiceError
import com.typesafe.config.Config
import cortex.api.ErrorResponse
import cortex.api.baile.TableReferenceResponse
import play.api.libs.json.JsString

class InternalTableRoutes(
  conf: Config,
  tableService: TableService
) extends BaseRoutes {

  val routes: Route =
    path("tables-references") {
      (get & parameters(('userId.as[UUID], 'tableIds.as(CsvSeq[String])))) { (userId, tablesIds) =>
        onSuccess(tableService.list(
          userId,
          tablesIds
        )) {
          case Right(tables) => complete(tables.map { table =>
            TableReferenceResponse(
              tableName = table.entity.databaseId,
              schema = table.entity.repositoryId
            )
          })
          case Left(e) => complete(translateError(e))
        }
      }
    }


  def translateError(error: InternalTableServiceError): (StatusCode, ErrorResponse) = error match {
    case InternalTableServiceError.AccessDenied =>
      (StatusCodes.Unauthorized, ErrorResponse(error = TableReferenceResponse.Errors.Table2))
    case InternalTableServiceError.TableNotFound(tableId) =>
      (
        StatusCodes.NotFound,
        ErrorResponse(
          error = TableReferenceResponse.Errors.Table1,
          details = Seq(JsString(tableId))
        )
      )
  }

}

