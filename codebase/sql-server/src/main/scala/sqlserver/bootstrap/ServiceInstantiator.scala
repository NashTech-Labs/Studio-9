package sqlserver.bootstrap

import akka.actor.{ ActorSystem, Scheduler }
import akka.event.LoggingAdapter
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import com.typesafe.config.Config
import sqlserver.bootstrap.ServiceInstantiator.HttpSettings
import sqlserver.dao.table.RedshiftTableDataDao
import sqlserver.services.baile.BaileService
import sqlserver.services.info.InfoService
import sqlserver.services.query.QueryService
import sqlserver.services.usermanagement.UMService
import sqlserver.utils.DurationConverter._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration

class ServiceInstantiator(conf: Config)(
  implicit system: ActorSystem,
  val logger: LoggingAdapter,
  materializer: ActorMaterializer
) {

  implicit val ec: ExecutionContext = system.dispatcher
  implicit val scheduler: Scheduler = system.scheduler

  lazy val infoService: InfoService = new InfoService

  lazy val umService: UMService = new UMService(
    settings = UMService.Settings(
      baseUrl = conf.getString("um-service.url"),
      responseTimeout = httpSettings.responseTimeout,
      firstRetryDelay = httpSettings.firstRetryDelay,
      retriesCount = httpSettings.retriesCount
    ),
    http
  )

  lazy val baileService = {
    val baileConfig = conf.getConfig("baile")
    new BaileService(
      settings = BaileService.Settings(
        baseUrl = baileConfig.getString("rest-url"),
        username = baileConfig.getString("user"),
        password = baileConfig.getString("password"),
        responseTimeout = httpSettings.responseTimeout,
        firstRetryDelay = httpSettings.firstRetryDelay,
        retriesCount = httpSettings.retriesCount
      ),
      http
    )
  }

  lazy val queryService: QueryService = new QueryService(tableDataDao, umService, baileService)

  private lazy val http = Http()

  private lazy val httpSettings = HttpSettings(
    responseTimeout = toScalaFiniteDuration(conf.getDuration("akka.http.client.response-timeout")),
    firstRetryDelay = toScalaFiniteDuration(conf.getDuration("akka.http.client.first-retry-delay")),
    retriesCount = conf.getInt("akka.http.client.max-retries")
  )

  private lazy val tabularConnectionInstantiator = new TabularConnectionInstantiator(conf.getConfig("db"))

  private lazy val tableDataDao = new RedshiftTableDataDao(
    tabularConnectionInstantiator.connectionProvider,
    conf.getInt("db.fetch-size")
  )

}

object ServiceInstantiator {

  case class HttpSettings(
    responseTimeout: FiniteDuration,
    firstRetryDelay: FiniteDuration,
    retriesCount: Int
  )

}
