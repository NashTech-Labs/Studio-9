package argo.rest

import argo.rest.modules.{ AkkaModule, HttpServerModule }
import argo.rest.modules._

object HttpServerApp extends App
  with SettingsModule
  with AkkaModule
  with LoggingModule
  with ServicesModule
  with AuthenticationModule
  with EndpointsModule
  with HttpServerModule