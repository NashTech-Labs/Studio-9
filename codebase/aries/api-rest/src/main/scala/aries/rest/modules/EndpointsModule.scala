package aries.rest.modules

import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Directives.{ complete, handleExceptions }
import akka.http.scaladsl.server.ExceptionHandler
import akka.http.scaladsl.server.RouteConcatenation._
import akka.http.scaladsl.server.directives.SecurityDirectives.authenticateBasic
import aries.common.elastic.ElasticSearchSettings
import aries.rest.common.BaseRoutes
import aries.rest.heartbeat.{ HeartbeatHttpEndpoint, HeartbeatSearchHttpEndpoint }
import aries.rest.job.{ JobHttpEndpoint, JobSearchHttpEndpoint }

trait EndpointsModule {
  self: SettingsModule with AkkaModule with ServicesModule with AuthenticationModule =>

  val jobHttpEndpoint = JobHttpEndpoint(jobCommand, jobQuery, config)
  val jobSearchHttpEndpoint = JobSearchHttpEndpoint(jobQuery, config)
  val heartbeatHttpEndpoint = HeartbeatHttpEndpoint(heartbeatCommand, heartbeatQuery, config)
  val heartbeatSearchHttpEndpoint = HeartbeatSearchHttpEndpoint(heartbeatQuery, config)

  val elasticSettings = ElasticSearchSettings(system)

  val myExceptionHandler = ExceptionHandler {
    // TODO: this Exceptions should be handled at service/repository layer and wrapped into ServiceExceptions.
    case _: java.net.UnknownHostException => {
      val host = elasticSettings.host
      complete(HttpResponse(InternalServerError, entity = s"Error connecting to elasticsearch. Could not find provided host: $host."))
    }

    case _: java.net.ConnectException => {
      val host = elasticSettings.host
      val port = elasticSettings.port
      complete(HttpResponse(InternalServerError, entity = s"Error connecting to elasticsearch. Connection refused at the provided host: $host and port: $port."))
    }
  }

  val routes = handleExceptions(myExceptionHandler) {
    BaseRoutes.routes(
      authenticateBasic(realm = "Secure command endpoint", commandAuthenticator.authenticate) { _ =>
        jobHttpEndpoint.routes ~
          heartbeatHttpEndpoint.routes
      } ~
        authenticateBasic(realm = "Secure search endpoint", searchAuthenticator.authenticate) { _ =>
          jobSearchHttpEndpoint.routes ~
            heartbeatSearchHttpEndpoint.routes
        }
    )
  }
}