package baile

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.scaladsl.server.{ PathMatcher0, Route }
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import com.typesafe.config.Config

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }

class HttpServer(httpConfig: Config)(
  implicit system: ActorSystem,
  executionContext: ExecutionContext,
  materializer: ActorMaterializer,
  logger: LoggingAdapter
) {

  def start(routes: Route): Future[Http.ServerBinding] = {
    val interface = httpConfig.getString("interface")
    val port = httpConfig.getInt("port")
    val prefixes = httpConfig.getString("prefix").split("/", -1).toList

    val prefixedRoutes = prefixes match {
      case Nil =>
        routes
      case firstPrefix :: restPrefixes =>
        val prefixMatcher = restPrefixes.foldLeft[PathMatcher0](firstPrefix)(_ / _)
        pathPrefix(prefixMatcher).apply(routes)
    }

    val serverBinding = Http().bindAndHandle(prefixedRoutes, interface, port)

    serverBinding.onComplete {
      case Success(_) => logger.info("Has bound server on {}:{}.", interface, port)
      case Failure(ex) => logger.error(ex, "Has failed to bind to {}:{}!", interface, port)
    }

    serverBinding
  }

}
