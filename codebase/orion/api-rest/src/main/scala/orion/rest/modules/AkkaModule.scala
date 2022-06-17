package orion.rest.modules

import akka.actor.ActorSystem
import akka.cluster.seed.ZookeeperClusterSeed
import akka.event.{ Logging, LoggingAdapter }
import akka.stream.ActorMaterializer

import scala.concurrent.ExecutionContextExecutor

trait AkkaModule {
  self: SettingsModule with LoggingModule =>

  implicit val system: ActorSystem = ActorSystem(s"${config.serviceConfig.name}-actor-system", config.config)

  implicit val executor: ExecutionContextExecutor = system.dispatcher
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  override implicit val logger: LoggingAdapter = Logging(system, config.serviceConfig.name)
}

trait AkkaClusterModule { self: AkkaModule with SettingsModule with LoggingModule =>
  logger.info(s"akka.remote.netty.tcp.hostname=[{}]", config.rootConfig.getString("akka.remote.netty.tcp.hostname"))
  logger.info(s"akka.remote.netty.tcp.port=[{}]", config.rootConfig.getString("akka.remote.netty.tcp.port"))
  logger.info(s"akka.remote.netty.tcp.bind-hostname=[{}]", config.rootConfig.getString("akka.remote.netty.tcp.bind-hostname"))
  logger.info(s"akka.remote.netty.tcp.bind-port=[{}]", config.rootConfig.getString("akka.remote.netty.tcp.bind-port"))

  ZookeeperClusterSeed(system).join()
}
