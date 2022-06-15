package orion.common.rest

import com.typesafe.config.{ Config, ConfigFactory }
import orion.common.config.ConfigFactoryExt
import orion.common.utils.DurationExtensions._

import scala.concurrent.duration.FiniteDuration
import scala.util.Try

trait BaseConfig {

  def config: Option[Config]

  def appRootSectionName: String

  val rootConfig: Config = {
    ConfigFactoryExt.enableEnvOverride()
    config match {
      case Some(c) => c.withFallback(ConfigFactory.load())
      case _       => ConfigFactory.load
    }
  }

  protected lazy val application: Config = rootConfig.getConfig(appRootSectionName)

  lazy val appName: String = application.getString("name")
  lazy val appVersion: String = application.getString("version")

  lazy val httpConfig: HttpConfig = {
    HttpConfig(
      rootConfig.getString("http.interface"),
      rootConfig.getInt("http.port"),
      rootConfig.getDuration("akka.http.server.request-timeout"),
      rootConfig.getString("http.search-user-name"),
      rootConfig.getString("http.search-user-password")
    )
  }

  lazy val serviceConfig: ServiceConfig = {
    ServiceConfig(appName, appVersion)
  }

  /** Attempts to acquire from environment, then java system properties. */
  def withFallback[T](env: Try[T]): Option[T] = env match {
    case value if Option(value).isEmpty => None
    case _                              => env.toOption
  }

  protected case class HttpConfig(
      interface:          String,
      port:               Int,
      requestTimeout:     FiniteDuration,
      searchUserName:     String,
      searchUserPassword: String
  ) {
    require(!searchUserName.isEmpty, "http.search-user-name")
    require(!searchUserPassword.isEmpty, "http.search-user-password")
  }

  protected case class ServiceConfig(name: String, version: String)

}
