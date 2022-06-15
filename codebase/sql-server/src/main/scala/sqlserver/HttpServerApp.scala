package sqlserver

import akka.actor.ActorSystem
import akka.event.{ Logging, LoggingAdapter }
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import sqlserver.bootstrap.{ RoutesInstantiator, ServiceInstantiator }

import scala.concurrent.ExecutionContext.global
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ Await, ExecutionContextExecutor, Future }
import scala.util.{ Failure, Success }

object HttpServerApp extends App {

  val conf = ConfigFactory.load()

  implicit val actorSystem: ActorSystem = ActorSystem("sql-server-actor-system")
  implicit val materializer: ActorMaterializer =
    ActorMaterializer()(actorSystem)
  implicit val logger: LoggingAdapter = Logging(actorSystem, "SQLServer")

  implicit val executionContext: ExecutionContextExecutor = global

  try {
    val httpServerConfig = conf.getConfig("http")
    val services = new ServiceInstantiator(conf)

    val httpServer = new HttpServer(httpServerConfig)(
      system = actorSystem,
      executionContext = actorSystem.dispatcher,
      materializer = materializer,
      logger = logger
    )

    val routes = new RoutesInstantiator(conf, services)(actorSystem.dispatcher, logger).routes

    val serverBinding: Future[Http.ServerBinding] = httpServer.start(routes)

    scala.sys.addShutdownHook {
      val cleanup = for {
        _ <- serverBinding.flatMap(_.unbind()).andThen {
          case Success(_) => logger.info("Has unbounded http server.")
          case Failure(ex) =>
            logger.error(ex, "Has failed to unbind http server.")
        }
        _ <- actorSystem.terminate().andThen {
          case Success(_) =>
            logger.info(s"Actor system $actorSystem has been terminated.")
          case Failure(ex) =>
            logger.error(ex, s"Has failed to stop actor system $actorSystem")
        }
      } yield ()

      Await.result(cleanup, 1.minute)
    }
  } catch {
    case e: Throwable =>
      logger.error(e, "Error starting application:")
      Await.result(actorSystem.terminate(), 30.seconds)
  }

}
