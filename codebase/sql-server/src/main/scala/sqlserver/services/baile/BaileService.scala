package sqlserver.services.baile

import java.util.UUID

import akka.actor.Scheduler
import akka.event.LoggingAdapter
import akka.http.scaladsl.HttpExt
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import cats.implicits._
import cortex.api.ErrorResponse
import cortex.api.baile.TableReferenceResponse
import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport
import play.api.libs.json.JsString
import sqlserver.services.baile.BaileService.{ DereferenceTablesError, Settings }
import sqlserver.services.baile.BaileService.DereferenceTablesError.{ AccessDenied, TableNotFound }
import sqlserver.services.http.HttpClientService

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ ExecutionContext, Future }

class BaileService(
  val settings: Settings,
  val http: HttpExt
)(
  implicit val ec: ExecutionContext,
  val materializer: Materializer,
  val logger: LoggingAdapter,
  val scheduler: Scheduler
) extends HttpClientService
    with PlayJsonSupport {

  override val responseTimeout: FiniteDuration = settings.responseTimeout
  override val firstRetryDelay: FiniteDuration = settings.firstRetryDelay
  override val retriesCount: Int = settings.retriesCount

  private[services] def dereferenceTables(
    userId: UUID,
    tableIds: Seq[String]
  ): Future[Either[DereferenceTablesError, List[TableReferenceResponse]]] =
    makeHttpRequest(
      HttpRequest(GET)
        .addCredentials(BasicHttpCredentials(settings.username, settings.password))
        .withUri(
          Uri(s"${settings.baseUrl}/v2.0/tables-references").withQuery(
            Query(
              "userId" -> userId.toString,
              "tableIds" -> tableIds.mkString(",")
            )
          )
        )
    ) {
      case StatusCodes.OK =>
        response: HttpResponse =>
          Unmarshal(response.entity).to[List[TableReferenceResponse]].map(_.asRight)
      case StatusCodes.Unauthorized =>
        _ =>
          Future.successful(AccessDenied.asLeft)
      case StatusCodes.NotFound =>
        response: HttpResponse =>
          Unmarshal(response.entity).to[ErrorResponse].map { errorResponse =>
            val tableId = errorResponse.details.head.as[JsString].value
            TableNotFound(tableId).asLeft
          }
    }

}

object BaileService {

  case class Settings(
    baseUrl: String,
    username: String,
    password: String,
    responseTimeout: FiniteDuration,
    firstRetryDelay: FiniteDuration,
    retriesCount: Int
  )

  sealed trait DereferenceTablesError

  object DereferenceTablesError {
    case object AccessDenied extends DereferenceTablesError
    case class TableNotFound(tableId: String) extends DereferenceTablesError
  }

}
