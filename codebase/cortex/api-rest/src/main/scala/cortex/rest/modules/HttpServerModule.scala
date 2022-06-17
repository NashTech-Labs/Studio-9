package cortex.rest.modules

import cortex.rest.common.HttpServer

trait HttpServerModule { self: SettingsModule with AkkaModule with EndpointsModule with LoggingModule =>
  HttpServer.start(routes)
}
