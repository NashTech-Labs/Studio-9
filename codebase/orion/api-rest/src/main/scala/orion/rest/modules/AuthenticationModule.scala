package orion.rest.modules

import orion.rest.common.ConfigUserPassAuthenticator

trait AuthenticationModule { self: SettingsModule =>

  val authenticator = ConfigUserPassAuthenticator(config.httpConfig.searchUserName, config.httpConfig.searchUserPassword)
}
