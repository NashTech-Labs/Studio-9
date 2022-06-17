package argo.rest.modules

import akka.http.scaladsl.server.directives.SecurityDirectives.authenticateBasic
import argo.rest.common.BaseRoutes
import argo.rest.config.ConfigSettingHttpEndpoint

trait EndpointsModule {
  self: SettingsModule with AkkaModule with ServicesModule with AuthenticationModule =>

  val configSettingHttpEndpoint = ConfigSettingHttpEndpoint(configSettingCommandService, configSettingQueryService, config)

  val routes = {
    BaseRoutes.routes(
      authenticateBasic(realm = "ConfigSetting HTTP endpoint", authenticator.authenticate) { _ =>
        configSettingHttpEndpoint.routes
      }
    )
  }
}