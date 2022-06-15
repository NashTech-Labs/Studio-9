package aries.rest

import aries.rest.modules._

object HttpServerApp extends App
  with SettingsModule
  with AkkaModule
  with LoggingModule
  with ServicesModule
  with AuthenticationModule
  with EndpointsModule
  with HttpServerModule