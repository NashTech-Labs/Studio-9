package taurus.job

import akka.actor.{ ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider }
import com.typesafe.config.Config

import scala.concurrent.duration.Duration

class CortexSettings(config: Config) extends Extension {
  private val cortexConfig = config.getConfig("cortex")

  val url = cortexConfig.getString("rest-url")
  val apiVersion = cortexConfig.getString("api-version")
  val baseUrl = s"$url/$apiVersion"
  val baseInputPath = cortexConfig.getString("input-directory")

  object credentials {
    val username = cortexConfig.getString("credentials.username")
    val password = cortexConfig.getString("credentials.password")
  }

  val requestRetryCount = cortexConfig.getInt("request-retry-count")

  val jobPollingMaxAttempts = cortexConfig.getInt("job-polling.max-attempts")
  val jobPollingInterval = Duration.fromNanos(cortexConfig.getDuration("job-polling.interval").toNanos)

}

object CortexSettings extends ExtensionId[CortexSettings] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): CortexSettings = new CortexSettings(system.settings.config)

  override def lookup(): ExtensionId[_ <: Extension] = CortexSettings
}
