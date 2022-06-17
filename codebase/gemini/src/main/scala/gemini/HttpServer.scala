package gemini

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives.pathPrefix
import akka.http.scaladsl.server.Route
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
    val prefix = httpConfig.getString("prefix")

    val prefixedRoutes = pathPrefix(prefix).apply(routes)

    val serverBinding = Http().bindAndHandle(prefixedRoutes, interface, port)

    serverBinding.onComplete {
      case Success(_) =>
        logger.info("Has bound server on {}:{}.", interface, port)
      case Failure(ex) =>
        logger.error(ex, "Has failed to bind to {}:{}!", interface, port)
    }

    serverBinding
  }

}
