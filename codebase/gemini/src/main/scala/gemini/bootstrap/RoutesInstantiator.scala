// $COVERAGE-OFF$
package gemini.bootstrap

import akka.http.scaladsl.server.Directives.{ concat, ignoreTrailingSlash }
import akka.http.scaladsl.server.Route
import com.typesafe.config.Config
import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport
import gemini.routes.BaseRoutes
import gemini.routes.info.InfoRoutes
import gemini.routes.jupyter.JupyterSessionRoutes
import resscheduler.routes.ResourceRoutes

import scala.concurrent.ExecutionContext

class RoutesInstantiator(
  conf: Config,
  services: ServiceInstantiator
)(
  implicit val ec: ExecutionContext
) extends PlayJsonSupport {

  private val infoRoutes = new InfoRoutes(services.infoService)

  private val jupyterSessionRoutes = {
    val httpAuthConfig = conf.getConfig("http.auth")
    val settings = JupyterSessionRoutes.Settings(
      basicAuthUsername = httpAuthConfig.getString("username"),
      basicAuthPassword = httpAuthConfig.getString("password"),
    )
    new JupyterSessionRoutes(
      settings,
      services.jupyterSessionService
    )
  }

  private val resourceRoutes =
    new ResourceRoutes(
      services.resourceProvider,
      _ => "Authentication failed"
    )

  val routes: Route = BaseRoutes.seal(conf) {
    ignoreTrailingSlash {
      concat(
        infoRoutes.routes,
        jupyterSessionRoutes.routes,
        resourceRoutes.routes
      )
    }
  }

}
