package baile.services.http

import akka.actor.Scheduler
import akka.event.LoggingAdapter
import akka.http.scaladsl.HttpExt
import akka.http.scaladsl.model._
import akka.pattern.after
import akka.stream.Materializer
import baile.services.http.exceptions.UnexpectedResponseException
import baile.utils.DurationConverter._
import com.typesafe.config.Config

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ ExecutionContext, Future }

trait HttpClientService {

  implicit val http: HttpExt
  implicit val materializer: Materializer
  implicit val ec: ExecutionContext
  implicit val logger: LoggingAdapter
  implicit val scheduler: Scheduler
  val conf: Config

  val responseTimeout: FiniteDuration = toScalaFiniteDuration(conf.getDuration("akka.http.client.response-timeout"))
  val firstRetryDelay: FiniteDuration = toScalaFiniteDuration(conf.getDuration("akka.http.client.first-retry-delay"))
  val retriesCount: Int = conf.getInt("akka.http.client.max-retries")

  def makeHttpRequest(
    request: HttpRequest,
    expectedCode: StatusCode = StatusCodes.OK
  ): Future[HttpResponse] = makeHttpRequest(request, Seq(expectedCode))

  def makeHttpRequest(
    request: HttpRequest,
    expectedCodes: Seq[StatusCode]
  ): Future[HttpResponse] = makeHttpRequest(request, expectedCodes, retriesCount, firstRetryDelay)

  private[http] def makeHttpRequest(
    request: HttpRequest,
    expectedCodes: Seq[StatusCode],
    retries: Int,
    nextTryIn: FiniteDuration
  ): Future[HttpResponse] = {

    def handleResponse(response: HttpResponse): Future[HttpResponse] =
      if (!expectedCodes.contains(response.status)) {
        response.entity.toStrict(responseTimeout).flatMap { entity =>
          val ex = UnexpectedResponseException(request, expectedCodes, response.status, entity)
          if (response.status.isFailure && request.method.isIdempotent && retries > 0) {
            logger.warning(s"Retrying request (retries left: ${ retries - 1 }). Reason: ${ ex.getMessage }")

            after(nextTryIn, scheduler) {
              makeHttpRequest(request, expectedCodes, retries - 1, nextTryIn * 2)
            }
          } else {
            logger.error(ex.getMessage)
            Future.failed(ex)
          }
        }
      } else {
        Future.successful(response)
      }

    for {
      response <- http.singleRequest(request)
      result <- handleResponse(response)
    } yield result

  }


}
