package taurus.rest.modules

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives
import taurus.rest.common.BaseRoutes

trait EndpointsModule { self: SettingsModule with AkkaModule with ServicesModule =>

  val routes = BaseRoutes.routes {
    // Route place holder until we have some actual routes
    import Directives._
    complete(StatusCodes.NotFound)
  }

}
