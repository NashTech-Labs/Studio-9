package argo.rest.common

import java.lang.management.ManagementFactory

import akka.event.Logging
import akka.http.scaladsl.server.{ Directives, Route }
import akka.stream.Materializer
import argo.common.json4s.DefaultJson4sSupport
import argo.common.rest.{ BaseConfig, CustomLogging }
import argo.common.rest.marshalling.Json4sHttpSupport
import argo.domain.rest.status.{ About, HealthCheckResponse, Status }
import argo.common.rest.{ BaseConfig, CustomLogging }
import argo.common.{ BuildInfo, ToDateHelper, now }
import argo.domain.rest.status.{ About, HealthCheckResponse }

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.{ Duration, _ }
import scala.language.{ implicitConversions, postfixOps }

trait InfoRoutes extends Directives with DefaultJson4sSupport with Json4sHttpSupport with CustomLogging {

  def infoRoutes(implicit config: BaseConfig): Route = {
    val serviceName = config.serviceConfig.name

    pathPrefix("status") {
      pathEndOrSingleSlash {
        get {
          val uptime = Duration(ManagementFactory.getRuntimeMXBean.getUptime, MILLISECONDS).toString()
          complete(Status(serviceName, uptime))
        }
      }
    } ~
      pathPrefix("about") {
        pathEndOrSingleSlash {
          get {
            complete(About(serviceName, now.toUtcIso, BuildInfo.version, BuildInfo.builtAtString))
          }
        }
      } ~
      pathPrefix("health") {
        pathEndOrSingleSlash {
          get {
            complete(HealthCheckResponse(true))
          }
        }
      }
  }

}

trait BaseRoutes extends InfoRoutes {
  def routes(serviceRoutes: Route)(implicit m: Materializer, ex: ExecutionContext, config: BaseConfig): Route = {
    val serviceVersion = config.serviceConfig.version

    pathPrefix(serviceVersion) {
      logRequestMethodAndResponseStatus(Logging.DebugLevel) {
        infoRoutes
      } ~
        logRequestMethodAndResponseStatus(Logging.InfoLevel) {
          serviceRoutes
        }
    } ~
      path("")(getFromResource("public/index.html"))
  }
}

object BaseRoutes extends BaseRoutes

