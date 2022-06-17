package pegasus.rest.modules

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer

trait AkkaModule { self: SettingsModule =>
  implicit val system = ActorSystem(s"${config.serviceConfig.name}-actor-system", config.config)
  implicit val executor = system.dispatcher
  implicit val materializer = ActorMaterializer()
}
