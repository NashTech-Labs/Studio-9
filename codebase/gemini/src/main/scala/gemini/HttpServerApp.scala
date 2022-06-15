// $COVERAGE-OFF$
package gemini

import akka.Done
import akka.actor.CoordinatedShutdown.Reason
import akka.actor.{ ActorSystem, CoordinatedShutdown }
import akka.cluster.seed.ZookeeperClusterSeed
import akka.event.{ Logging, LoggingAdapter }
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import gemini.bootstrap.{ RoutesInstantiator, ServiceInstantiator }

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.global
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ Await, ExecutionContextExecutor, Future }
import scala.util.{ Failure, Success }

object HttpServerApp extends App {

  val conf = ConfigFactory.load()

  implicit val actorSystem: ActorSystem = ActorSystem("gemini-actor-system", conf)
  implicit val materializer: ActorMaterializer = ActorMaterializer()(actorSystem)
  implicit val logger: LoggingAdapter = Logging(actorSystem, "Gemini")

  implicit val executionContext: ExecutionContextExecutor = global

  val akkaShutdown = CoordinatedShutdown(actorSystem)

  logger.info("List of environment variables: \n" + System.getenv().asScala.mkString("\n"))

  try {
    ZookeeperClusterSeed(actorSystem).join()
    val httpServerConfig = conf.getConfig("http")
    val services = new ServiceInstantiator(conf)

    val httpServer = new HttpServer(httpServerConfig)(
      system = actorSystem,
      executionContext = actorSystem.dispatcher,
      materializer = materializer,
      logger = logger
    )

    val routes = new RoutesInstantiator(conf, services)(actorSystem.dispatcher).routes

    val serverBinding: Future[Http.ServerBinding] = httpServer.start(routes).andThen {
      case Failure(ex) => shutdown(ex)
    }

    akkaShutdown.addTask(CoordinatedShutdown.PhaseServiceUnbind, "Unbinding public http server") { () =>
      serverBinding.transformWith {
        case Success(binding) =>
          binding.unbind().andThen {
            case Success(_) => logger.info("Has unbounded public http server.")
            case Failure(ex) => logger.error(ex, "Has failed to unbind public http server.")
          }
        case Failure(_) => Future.successful(Done)
      }
    }
  } catch {
    case e: Throwable =>
      Await.result(shutdown(e), 30.seconds)
  }

  private def shutdown(e: Throwable): Future[Done] = {
    logger.error(e, "Error starting application:")
    akkaShutdown.run(new Reason {
      override def toString: String = "Error starting application: " ++ e.getMessage
    })
  }

}
