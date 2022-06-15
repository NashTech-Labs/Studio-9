package sqlserver.services.http

import akka.actor.Scheduler
import akka.event.LoggingAdapter
import akka.http.scaladsl.HttpExt
import akka.http.scaladsl.model._
import akka.pattern.after
import akka.stream.Materializer
import sqlserver.services.http.exceptions.UnexpectedResponseException

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ ExecutionContext, Future }

trait HttpClientService {

  implicit val http: HttpExt
  implicit val materializer: Materializer
  implicit val ec: ExecutionContext
  implicit val logger: LoggingAdapter
  implicit val scheduler: Scheduler

  val responseTimeout: FiniteDuration
  val firstRetryDelay: FiniteDuration
  val retriesCount: Int

  def makeHttpRequest(
    request: HttpRequest,
    expectedCode: StatusCode
  ): Future[HttpResponse] = makeHttpRequest(request, Seq(expectedCode))

  def makeHttpRequest(
    request: HttpRequest,
    expectedCodes: Seq[StatusCode]
  ): Future[HttpResponse] =
    makeHttpRequest(request) {
      case code if expectedCodes.contains(code) =>
        response =>
          Future.successful(response)
    }

  def makeHttpRequest[T](
    request: HttpRequest
  )(handlerSelector: PartialFunction[StatusCode, HttpResponse => Future[T]]): Future[T] =
    makeHttpRequest(
      request,
      retriesCount,
      firstRetryDelay
    )(handlerSelector)

  private[http] def makeHttpRequest[T](
    request: HttpRequest,
    retries: Int,
    nextTryIn: FiniteDuration
  )(handlerSelector: PartialFunction[StatusCode, HttpResponse => Future[T]]): Future[T] = {

    def handleResponse(response: HttpResponse): Future[T] =
      handlerSelector.lift(response.status) match {
        case Some(f) =>
          f(response)
        case None =>
          response.entity.toStrict(responseTimeout).flatMap { entity =>
            val ex = UnexpectedResponseException(request, response.status, entity)
            if (response.status.isFailure && request.method.isIdempotent && retries > 0) {
              logger.warning(s"Retrying request (retries left: ${retries - 1}). Reason: ${ex.getMessage}")

              after(nextTryIn, scheduler) {
                makeHttpRequest(request, retries - 1, nextTryIn * 2)(handlerSelector)
              }
            } else {
              logger.error(ex.getMessage)
              Future.failed(ex)
            }
          }
      }

    for {
      response <- http.singleRequest(request)
      result <- handleResponse(response)
    } yield result

  }

}
