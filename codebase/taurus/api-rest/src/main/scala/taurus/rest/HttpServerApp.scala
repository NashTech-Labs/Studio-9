package taurus.rest

import taurus.rest.modules._

object HttpServerApp extends App
  with SettingsModule
  with AkkaModule
  with LoggingModule
  with ServicesModule
  with EndpointsModule
  with HttpServerModule