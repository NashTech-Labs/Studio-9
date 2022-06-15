package baile

import akka.Done
import akka.actor.CoordinatedShutdown.Reason
import akka.actor.{ ActorSystem, CoordinatedShutdown }
import akka.cluster.seed.ZookeeperClusterSeed
import akka.event.{ Logging, LoggingAdapter }
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import baile.bootstrap._
import baile.utils.ThrowableExtensions._
import com.typesafe.config.ConfigFactory
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.{ MongoClient, MongoDatabase }

import scala.concurrent.ExecutionContext.global
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ Await, ExecutionContextExecutor, Future }
import scala.util.{ Failure, Success, Try }

// scalastyle:off field.name
object HttpServerApp extends App {
  val conf = ConfigFactory.load()

  val actorSystem = ActorSystem("baile-actor-system")
  val materializer: ActorMaterializer = ActorMaterializer()(actorSystem)
  implicit val logger: LoggingAdapter = Logging(actorSystem, "Baile")

  implicit val executionContext: ExecutionContextExecutor = global

  val akkaShutdown = CoordinatedShutdown(actorSystem)

  try {
    ZookeeperClusterSeed(actorSystem).join()

    val baseAppUrl = conf.getString("base-app-url")
    val publicHttpServerConfig = conf.getConfig("http")
    val appHttpPrefix = publicHttpServerConfig.getString("prefix")
    val appUrl = s"$baseAppUrl/$appHttpPrefix"

    val publicHttpServer = new HttpServer(publicHttpServerConfig)(
      system = actorSystem,
      executionContext = actorSystem.dispatcher,
      materializer = materializer,
      logger = logger
    )

    val privateHttpServer = new HttpServer(conf.getConfig("private-http"))(
      system = actorSystem,
      executionContext = actorSystem.dispatcher,
      materializer = materializer,
      logger = logger
    )

    val mongoClient = MongoClient(conf.getString("mongo.url"))
    val mongoDatabase = mongoClient.getDatabase(conf.getString("mongo.db-name"))

    tryConnectToMongo(mongoDatabase)

    val daoInstantiator = new DaoInstantiator(mongoDatabase)
    val services = new ServiceInstantiator(
      conf = conf,
      defaultValuesConfig = ConfigFactory.load("default-values"),
      daoInstantiator = daoInstantiator
    )(actorSystem, logger, materializer)

    val publicRoutes = new RoutesInstantiator(conf, services, appUrl)(actorSystem.dispatcher).routes
    val privateRoutes = new PrivateRoutesInstantiator(conf, services)(actorSystem.dispatcher).routes

    val publicServerBinding: Future[Http.ServerBinding] = publicHttpServer.start(publicRoutes).andThen {
      case Failure(ex) => shutdown(ex)
    }
    val privateServerBinding: Future[Http.ServerBinding] = privateHttpServer.start(privateRoutes).andThen {
      case Failure(ex) => shutdown(ex)
    }

    akkaShutdown.addTask(CoordinatedShutdown.PhaseServiceUnbind, "Unbinding public http server") { () =>
      publicServerBinding.transformWith {
        case Success(binding) => binding.unbind()
          .andThen {
            case Success(_) => logger.info("Has unbounded public http server.")
            case Failure(ex) => logger.error(ex, "Has failed to unbind public http server.")
          }
        case Failure(_) => Future.successful(Done)
      }
    }

    akkaShutdown.addTask(CoordinatedShutdown.PhaseServiceUnbind, "Unbinding private http server") { () =>
      privateServerBinding.transformWith {
        case Success(binding) => binding.unbind()
          .andThen {
            case Success(_) => logger.info("Has unbounded private http server.")
            case Failure(ex) => logger.error(ex, "Has failed to unbind private http server.")
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

  private def tryConnectToMongo(mongoDatabase: MongoDatabase) =
    Try(Await.result(mongoDatabase.runCommand(Document("ping" -> 1)).toFuture, 30.seconds)) match {
      case Failure(exception) =>
        logger.error(s"Could not connect to Mongo on bootstrap. Error: ${ exception.printInfo }")
        throw exception
      case Success(value) =>
        value
    }

}
