package cortex.rest.modules

import akka.event.Logging

trait LoggingModule {
  self: SettingsModule with AkkaModule =>

  implicit val logger = Logging(system, config.serviceConfig.name)
}
