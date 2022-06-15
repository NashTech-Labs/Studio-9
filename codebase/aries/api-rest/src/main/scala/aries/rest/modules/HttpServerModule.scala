package aries.rest.modules

import aries.rest.common.HttpServer

trait HttpServerModule {
  self: SettingsModule with AkkaModule with EndpointsModule with LoggingModule =>
  HttpServer.start(routes)
}
