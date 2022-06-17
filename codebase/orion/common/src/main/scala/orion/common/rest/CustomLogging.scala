package orion.common.rest

import akka.event.Logging.LogLevel
import akka.event.LoggingAdapter
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.server.{ Route, RouteResult }
import akka.http.scaladsl.server.RouteResult.Complete
import akka.http.scaladsl.server.directives.{ DebuggingDirectives, LogEntry, LoggingMagnet }
import akka.stream.Materializer

import scala.concurrent.ExecutionContext
import scala.language.implicitConversions

trait CustomLogging {

  //override the standard implementation that creates multi lines log records
  implicit def forRequestResponseFromMarkerAndLevel(markerAndLevel: (String, LogLevel)): LoggingMagnet[HttpRequest ⇒ RouteResult ⇒ Unit] =
    LoggingMagnet.forRequestResponseFromFullShow {
      val (marker, level) = markerAndLevel
      request ⇒ response ⇒ Some(
        LogEntry("Response for Request : [" + request + "] Response: [" + response + "] ", marker, level)
      )
    }

  def logRequestMethodAndResponseStatus(level: LogLevel)(route: Route)(implicit m: Materializer, ex: ExecutionContext): Route = {

    def akkaResponseTimeLoggingFunction(loggingAdapter: LoggingAdapter, requestTimestamp: Long)(req: HttpRequest)(res: Any): Unit = {
      val entry = res match {
        case Complete(resp) =>
          val responseTimestamp: Long = System.currentTimeMillis()
          val elapsedTime: Long = responseTimestamp - requestTimestamp
          val loggingString = "Logged Request:" + req.method + ":" + req.uri + ":[" + resp.status + "] ElapsedTime:[" + elapsedTime + "]ms"
          LogEntry(loggingString, level)
        case anythingElse =>
          LogEntry(s"$anythingElse", level)
      }
      entry.logTo(loggingAdapter)
    }
    DebuggingDirectives.logRequestResult(LoggingMagnet(log => {
      val requestTimestamp = System.currentTimeMillis()
      akkaResponseTimeLoggingFunction(log, requestTimestamp)
    }))(route)

  }
}
