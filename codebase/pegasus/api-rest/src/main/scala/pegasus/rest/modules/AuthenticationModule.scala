package pegasus.rest.modules

import pegasus.rest.common.ConfigUserPassAuthenticator

trait AuthenticationModule { self: SettingsModule =>

  val authenticator = ConfigUserPassAuthenticator(config.httpConfig.authUserName, config.httpConfig.authUserPassword)

}
