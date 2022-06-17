package orion.rest.modules

import akka.http.scaladsl.model.StatusCodes
import orion.rest.common.BaseRoutes
import akka.http.scaladsl.server.{ Directives, Route }
import akka.http.scaladsl.server.directives.SecurityDirectives.authenticateBasic

trait EndpointsModule { self: SettingsModule with AkkaModule with ServicesModule with AuthenticationModule =>

  // Endpoints should be instantiated here

  val routes: Route = BaseRoutes.routes(
    authenticateBasic(realm = "Secure search endpoint", authenticator.authenticate) { _ =>
      // Route place holder until we have the actual routes
      import Directives._
      complete(StatusCodes.OK)
    }
  )
}