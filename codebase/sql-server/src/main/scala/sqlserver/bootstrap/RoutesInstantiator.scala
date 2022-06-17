package sqlserver.bootstrap

import akka.event.LoggingAdapter
import akka.http.scaladsl.server.Directives.{ concat, ignoreTrailingSlash }
import akka.http.scaladsl.server.Route
import com.typesafe.config.Config
import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport
import sqlserver.routes.BaseRoutes
import sqlserver.routes.info.InfoRoutes
import sqlserver.routes.query.QueryRoutes

import scala.concurrent.ExecutionContext

class RoutesInstantiator(
  conf: Config,
  services: ServiceInstantiator
)(
  implicit val ec: ExecutionContext,
  logger: LoggingAdapter
) extends PlayJsonSupport {

  private val infoRoutes = new InfoRoutes(services.infoService)

  private val queryRoutes = new QueryRoutes(services.queryService)

  val routes: Route = BaseRoutes.seal(conf) {
    ignoreTrailingSlash {
      concat(
        infoRoutes.routes,
        queryRoutes.routes
      )
    }
  }

}
