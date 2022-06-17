package orion.rest.modules

import orion.rest.common.HttpServer

trait HttpServerModule { self: SettingsModule with AkkaModule with EndpointsModule with LoggingModule =>
  HttpServer.start(routes)
}
