package pegasus.rest

import pegasus.rest.common.BaseRoutes
import pegasus.rest.modules.{ AkkaModule, HttpServerModule }
import pegasus.rest.modules._

object HttpServerApp extends App
  with SettingsModule
  with AkkaModule
  with LoggingModule
  with ServicesModule
  with BaseRoutes
  with HttpServerModule