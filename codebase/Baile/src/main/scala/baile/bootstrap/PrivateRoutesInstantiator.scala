package baile.bootstrap

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.Credentials
import baile.routes.BaseRoutes
import baile.routes.internal.{ CVOnlinePredictionRoutes, InternalTableRoutes }
import com.typesafe.config.{ Config }
import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport

import scala.concurrent.ExecutionContext

class PrivateRoutesInstantiator(
  conf: Config,
  services: ServiceInstantiator
)(
  implicit val ec: ExecutionContext
) extends PlayJsonSupport {

  private val cvOnlinePredictionRoutes: CVOnlinePredictionRoutes = new CVOnlinePredictionRoutes(
    conf,
    services.cvOnlinePredictionService
  )
  private val internalTableRoutes: InternalTableRoutes = new InternalTableRoutes(
    conf,
    services.tableService
  )

  val routes: Route = ignoreTrailingSlash {
    BaseRoutes.seal(conf) {
      authenticateBasic("internal services", authenticate) { _ =>
        concat(
          cvOnlinePredictionRoutes.routes,
          internalTableRoutes.routes
        )
      }
    }
  }

  private def authenticate(credentials: Credentials): Option[Unit] = {
    val userName = conf.getString("private-http.username")
    val userPassword = conf.getString("private-http.password")

    credentials match {
      case p@Credentials.Provided(authUserName) =>
        if (authUserName == userName && p.verify(userPassword)) Some(())
        else None
      case _ => None
    }
  }

}
