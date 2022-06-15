package gemini.routes.info

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import gemini.routes.BaseRoutes
import gemini.routes.contract.info.{ HealthCheckResponse, StatusResponse }
import gemini.services.info.InfoService

class InfoRoutes(service: InfoService) extends BaseRoutes {

  val routes: Route =
    concat(
      path("status") {
        get {
          onSuccess(service.getUptime) { status =>
            complete(StatusResponse.fromDomain(status))
          }
        }
      },
      path("health") {
        get {
          complete(HealthCheckResponse(true))
        }
      }
    )

}
