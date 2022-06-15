package sqlserver.services.dremio

import akka.actor.{ Actor, Props, Scheduler }
import akka.event.LoggingAdapter
import akka.http.scaladsl.HttpExt
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.{ HttpMethods, HttpRequest, HttpResponse, RequestEntity, StatusCodes }
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.pattern.pipe
import akka.stream.Materializer
import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport
import sqlserver.services.dremio.DremioService._
import sqlserver.services.dremio.datacontract._
import sqlserver.services.http.HttpClientService

import scala.concurrent.{ Await, ExecutionContext, Future }
import scala.concurrent.duration.FiniteDuration

class DremioService(
  val http: HttpExt,
  val settings: Settings,
  val initialTokenTimeout: FiniteDuration,
  val tokenRefreshPeriod: FiniteDuration
)(
  implicit val materializer: Materializer,
  val ec: ExecutionContext,
  val logger: LoggingAdapter,
  val scheduler: Scheduler
) extends HttpClientService
    with PlayJsonSupport
    with Actor {

  override val responseTimeout: FiniteDuration = settings.responseTimeout
  override val firstRetryDelay: FiniteDuration = settings.firstRetryDelay
  override val retriesCount: Int = settings.retriesCount

  private val baseUrl = settings.baseUrl

  private var token: Option[String] = None

  override def receive: Receive = {
    case SubmitSqlJob(sql) => submitSqlJob(sql) pipeTo sender()
    case GetJob(id) => getJob(id) pipeTo sender()
    case CancelJob(id) => cancelJob(id) pipeTo sender()
    case GetJobResultPage(jobId, offset, limit) => getJobResultPage(jobId, offset, limit) pipeTo sender()
    case RefreshToken => refreshToken() pipeTo sender()
  }

  override def preStart(): Unit = {
    super.preStart()
    Await.result(refreshToken(), initialTokenTimeout)
    scheduler.schedule(tokenRefreshPeriod, tokenRefreshPeriod, self, RefreshToken)
  }

  private def submitSqlJob(sql: String): Future[SQLJobSubmittedResponse] =
    for {
      requestEntity <- Marshal(SQLRequest(sql, Some(List(settings.space)))).to[RequestEntity]
      request = HttpRequest(
        method = HttpMethods.POST,
        uri = s"$baseUrl/api/v3/sql",
        entity = requestEntity
      )
      response <- makeSecuredHttpRequest(request)
      result <- Unmarshal(response.entity).to[SQLJobSubmittedResponse]
    } yield result

  private def getJob(jobId: String): Future[SQLJobResponse] =
    for {
      response <- makeSecuredHttpRequest(
        HttpRequest(
          method = HttpMethods.GET,
          uri = s"$baseUrl/api/v3/job/$jobId"
        )
      )
      result <- Unmarshal(response.entity).to[SQLJobResponse]
    } yield result

  private def cancelJob(jobId: String): Future[Unit] =
    makeSecuredHttpRequest(
      HttpRequest(
        method = HttpMethods.POST,
        uri = s"$baseUrl/api/v3/job/$jobId/cancel"
      )
    ).map(_ => ())

  private def getJobResultPage(jobId: String, offset: Int, limit: Int): Future[SQLJobResultResponse] =
    for {
      response <- makeSecuredHttpRequest(
        HttpRequest(
          method = HttpMethods.GET,
          uri = s"$baseUrl/api/v3/job/$jobId/results?offset=$offset&limit=$limit"
        )
      )
      result <- Unmarshal(response.entity).to[SQLJobResultResponse]
    } yield result

  private def makeSecuredHttpRequest(request: HttpRequest): Future[HttpResponse] =
    for {
      token <- this.token.fold(refreshToken())(Future.successful(_))
      header = RawHeader("Authorization", s"_dremio$token")
      result <- makeHttpRequest(request.withHeaders(header), StatusCodes.OK)
    } yield result

  private def refreshToken(): Future[String] =
    for {
      requestEntity <- Marshal(AuthenticationRequest(settings.username, settings.password)).to[RequestEntity]
      response <- makeHttpRequest(
        HttpRequest(
          method = HttpMethods.POST,
          uri = s"$baseUrl/apiv2/login",
          entity = requestEntity
        ),
        StatusCodes.OK
      )
      authenticationResponse <- Unmarshal(response.entity).to[AuthenticationResponse]
      newToken = authenticationResponse.token
    } yield {
      token = Some(authenticationResponse.token)
      newToken
    }

}

object DremioService {

  case class Settings(
    baseUrl: String,
    username: String,
    password: String,
    space: String,
    responseTimeout: FiniteDuration,
    firstRetryDelay: FiniteDuration,
    retriesCount: Int
  )

  case class SubmitSqlJob(sql: String)
  case class GetJob(jobId: String)
  case class CancelJob(jobId: String)
  case class GetJobResultPage(jobId: String, offset: Int, limit: Int)

  private case object RefreshToken

  def props(
    http: HttpExt,
    settings: Settings,
    initialTokenTimeout: FiniteDuration,
    tokenRefreshPeriod: FiniteDuration
  )(
    implicit materializer: Materializer,
    ec: ExecutionContext,
    logger: LoggingAdapter,
    scheduler: Scheduler
  ): Props = Props(new DremioService(http, settings, initialTokenTimeout, tokenRefreshPeriod))

}
