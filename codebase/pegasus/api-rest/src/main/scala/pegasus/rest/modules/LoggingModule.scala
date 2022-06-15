package pegasus.rest.modules

import akka.event.Logging

//TODO find a better place for this module
trait LoggingModule {
  self: SettingsModule with AkkaModule =>

  implicit val logger = Logging(system, config.serviceConfig.name)
}
