package aries.rest.modules

import aries.rest.common.ConfigUserPassAuthenticator

trait AuthenticationModule { self: SettingsModule =>

  val searchAuthenticator = ConfigUserPassAuthenticator(config.httpConfig.searchUserName, config.httpConfig.searchUserPassword)
  val commandAuthenticator = ConfigUserPassAuthenticator(config.httpConfig.commandUserName, config.httpConfig.commandUserPassword)

}
