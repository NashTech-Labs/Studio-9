package taurus.job

import akka.actor.{ ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider }
import com.typesafe.config.Config

class AriesSettings(config: Config) extends Extension {
  private val ariesConfig = config.getConfig("aries")

  val url = ariesConfig.getString("rest-url")
  val apiVersion = ariesConfig.getString("api-version")
  val baseUrl = s"$url/$apiVersion"

  object credentials {
    val username = ariesConfig.getString("credentials.username")
    val password = ariesConfig.getString("credentials.password")
  }

  val requestRetryCount = ariesConfig.getInt("request-retry-count")

}

object AriesSettings extends ExtensionId[AriesSettings] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): AriesSettings = new AriesSettings(system.settings.config)

  override def lookup(): ExtensionId[_ <: Extension] = AriesSettings
}
