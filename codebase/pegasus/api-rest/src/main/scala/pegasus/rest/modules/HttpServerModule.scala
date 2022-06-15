package pegasus.rest.modules

import pegasus.rest.common.{ BaseRoutes, HttpServer }

trait HttpServerModule {
  self: SettingsModule with AkkaModule with BaseRoutes with LoggingModule =>
  HttpServer.start(routes)
}
