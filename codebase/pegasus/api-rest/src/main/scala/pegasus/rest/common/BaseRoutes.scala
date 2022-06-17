package pegasus.rest.common

import java.lang.management.ManagementFactory

import akka.event.Logging
import akka.http.scaladsl.server.{ Directives, Route }
import akka.stream.Materializer
import pegasus.common.json4s.DefaultJson4sSupport
import pegasus.common.rest.marshalling.Json4sHttpSupport
import pegasus.domain.rest.status.Status
import pegasus.common.rest.{ BaseConfig, CustomLogging }
import pegasus.common.{ BuildInfo, ToDateHelper, now }
import pegasus.domain.rest.status.{ About, HealthCheckResponse }

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
  def routes(implicit m: Materializer, ex: ExecutionContext, config: BaseConfig): Route = {
    val serviceVersion = config.serviceConfig.version

    pathPrefix(serviceVersion) {
      logRequestMethodAndResponseStatus(Logging.DebugLevel) {
        infoRoutes
      }
    } ~
      path("")(getFromResource("public/index.html"))
  }
}

object BaseRoutes extends BaseRoutes

