package taurus.common.rest

import java.net.InetAddress

import com.typesafe.config.{ Config, ConfigFactory }
import taurus.common.config.ConfigFactoryExt
import taurus.common.utils.DurationExtensions._

import scala.concurrent.duration.FiniteDuration
import scala.util.Try

trait BaseConfig {

  def config: Option[Config] = Some(ConfigFactory.load)

  def appRootSectionName: String

  val localAddress: String = InetAddress.getLocalHost.getHostAddress

  val rootConfig: Config = {
    ConfigFactoryExt.enableEnvOverride()
    config match {
      case Some(c) => c.withFallback(ConfigFactory.load())
      case _       => ConfigFactory.load
    }
  }

  protected val application: Config = rootConfig.getConfig(appRootSectionName)

  val appName: String = application.getString("name")
  val appVersion: String = application.getString("version")

  val streamId: String = rootConfig.getString("stream-id")

  val httpConfig: HttpConfig = {
    HttpConfig(
      rootConfig.getString("http.interface"),
      rootConfig.getInt("http.port"),
      rootConfig.getDuration("akka.http.server.request-timeout")
    )
  }
  val serviceConfig: ServiceConfig = {
    ServiceConfig(appName, appVersion)
  }

  /** Attempts to acquire from environment, then java system properties. */
  def withFallback[T](env: Try[T]): Option[T] = env match {
    case value if Option(value).isEmpty => None
    case _                              => env.toOption
  }

  protected case class HttpConfig(
    interface:      String,
    port:           Int,
    requestTimeout: FiniteDuration
  )

  protected case class ServiceConfig(name: String, version: String)

}
