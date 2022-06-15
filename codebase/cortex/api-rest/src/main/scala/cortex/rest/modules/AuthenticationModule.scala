package cortex.rest.modules

import cortex.rest.common.ConfigUserPassAuthenticator

trait AuthenticationModule { self: SettingsModule =>

  val authenticator = ConfigUserPassAuthenticator(config.httpConfig.searchUserName, config.httpConfig.searchUserPassword)

}
