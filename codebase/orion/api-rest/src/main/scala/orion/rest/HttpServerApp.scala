package orion.rest

import orion.rest.modules._

object HttpServerApp extends App
  with SettingsModule
  with AkkaModule
  with AkkaClusterModule
  with LoggingModule
  with ServicesModule
  with AuthenticationModule
  with EndpointsModule
  with HttpServerModule
