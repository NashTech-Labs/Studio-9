package taurus.rest.modules

import taurus.rest.common.HttpServer

trait HttpServerModule { self: SettingsModule with AkkaModule with EndpointsModule with LoggingModule =>
  HttpServer.start(routes)
}
