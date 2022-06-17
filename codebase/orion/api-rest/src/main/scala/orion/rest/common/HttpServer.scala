package orion.rest.common

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import orion.common.rest.BaseConfig

import scala.concurrent.{ Await, ExecutionContext }
import scala.concurrent.duration._
import scala.util.{ Failure, Success }

object HttpServer {
  def start(routes: Route)(implicit system: ActorSystem, executor: ExecutionContext, materializer: ActorMaterializer, logger: LoggingAdapter, config: BaseConfig): Unit = {
    val (host, port) = (config.httpConfig.interface, config.httpConfig.port)
    val serverBinding = Http().bindAndHandle(routes, host, port)

    scala.sys.addShutdownHook {
      serverBinding.flatMap(_.unbind())
      system.terminate()
      logger.info(s"${system.name} has been terminated")
      Await.result(system.whenTerminated, 1 minute)
    }

    serverBinding.onComplete {
      case Success(_)  => logger.info("Has been running on {}:{}...", host, port)
      case Failure(ex) => logger.error(ex, "Failed to bind to {}:{}!", host, port)
    }
  }
}