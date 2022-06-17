package argo.rest.modules

import argo.rest.common.HttpServer

trait HttpServerModule {
  self: SettingsModule with AkkaModule with EndpointsModule with LoggingModule =>
  HttpServer.start(routes)
}
