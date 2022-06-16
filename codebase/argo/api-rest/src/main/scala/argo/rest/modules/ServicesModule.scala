package argo.rest.modules

import argo.service.config.{ ConfigSettingCommandService, ConfigSettingQueryService }

trait ServicesModule {
  self: AkkaModule =>

  val configSettingQueryService = system.actorOf(ConfigSettingQueryService.props(), ConfigSettingQueryService.Name)
  val configSettingCommandService = system.actorOf(ConfigSettingCommandService.props(configSettingQueryService), ConfigSettingCommandService.Name)
}
