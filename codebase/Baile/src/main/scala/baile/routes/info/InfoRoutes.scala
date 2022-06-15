package baile.routes.info

import java.lang.management.ManagementFactory

import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives._
import baile.routes.BaseRoutes
import baile.routes.contract.info.{ HealthCheckResponse, StatusResponse }

import scala.concurrent.duration.{ Duration, MILLISECONDS }

class InfoRoutes extends BaseRoutes {

  val routes: Route =
    path("status") {
      get {
        complete(StatusResponse(Duration(ManagementFactory.getRuntimeMXBean.getUptime, MILLISECONDS)))
      }
    } ~
    path("health") {
      get {
        complete(HealthCheckResponse(true))
      }
    }

}
