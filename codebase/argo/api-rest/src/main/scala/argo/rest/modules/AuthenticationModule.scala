package argo.rest.modules

import argo.rest.common.ConfigUserPassAuthenticator

trait AuthenticationModule { self: SettingsModule =>

  val authenticator = ConfigUserPassAuthenticator(config.httpConfig.authUserName, config.httpConfig.authUserPassword)

}
