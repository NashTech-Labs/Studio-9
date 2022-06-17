package sqlserver.services.usermanagement

import java.net.URLEncoder

import akka.actor.Scheduler
import akka.event.LoggingAdapter
import akka.http.scaladsl.HttpExt
import akka.http.scaladsl.model.{ HttpMethods, HttpRequest, StatusCodes }
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import cats.data.EitherT
import cats.implicits._
import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport
import sqlserver.services.http.HttpClientService
import sqlserver.services.usermanagement.UMService.{ Settings, TokenValidationError }
import sqlserver.services.usermanagement.UMService.TokenValidationError.InvalidToken
import sqlserver.services.usermanagement.datacontract.UserResponse

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ ExecutionContext, Future }

class UMService(
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

  def validateAccessToken(token: String): Future[Either[TokenValidationError, UserResponse]] = {
    val result = for {
      encodedToken <- EitherT.cond[Future](
        token.nonEmpty,
        URLEncoder.encode(token, "UTF-8"),
        InvalidToken
      )
      response <- EitherT.right[TokenValidationError](
        makeHttpRequest(
          HttpRequest(
            method = HttpMethods.GET,
            uri = s"${settings.baseUrl}/token/$encodedToken/user"
          ),
          Seq(StatusCodes.OK, StatusCodes.BadRequest)
        )
      )
      user <- EitherT[Future, TokenValidationError, UserResponse](response.status match {
        case StatusCodes.OK => Unmarshal(response).to[UserResponse].map(_.asRight)
        case _ => Future.successful(InvalidToken.asLeft)
      })
    } yield user

    result.value
  }

}

object UMService {

  case class Settings(
    baseUrl: String,
    responseTimeout: FiniteDuration,
    firstRetryDelay: FiniteDuration,
    retriesCount: Int
  )

  sealed trait TokenValidationError

  object TokenValidationError {

    case object InvalidToken extends TokenValidationError

  }

}
