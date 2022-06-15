package cortex.rest.modules

import cortex.rest.common.BaseRoutes
import cortex.rest.job.{ JobHttpEndpoint, JobSearchHttpEndpoint, JobStatusHttpEndpoint }
import akka.http.scaladsl.server.RouteConcatenation._
import akka.http.scaladsl.server.directives.SecurityDirectives.authenticateBasic

trait EndpointsModule { self: SettingsModule with AkkaModule with ServicesModule with AuthenticationModule =>

  val jobHttpEndpoint = JobHttpEndpoint(jobCommand, jobQuery, config)
  val jobSearchHttpEndpoint = JobSearchHttpEndpoint(jobQuery, config)
  val jobStatusHttpEndpoint = JobStatusHttpEndpoint(jobQuery, config)

  val routes = BaseRoutes.routes(
    authenticateBasic(realm = "Secure search endpoint", authenticator.authenticate) { _ =>
      jobHttpEndpoint.routes ~
        jobSearchHttpEndpoint.routes ~
        jobStatusHttpEndpoint.routes
    }
  )

}
