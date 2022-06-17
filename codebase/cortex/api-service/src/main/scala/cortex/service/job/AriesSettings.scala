package cortex.service.job

import akka.actor.{ ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider }
import com.typesafe.config.Config

class AriesSettings(config: Config) extends Extension {
  private val ariesConfig = config.getConfig("aries")

  val url = ariesConfig.getString("rest-url")
  val apiVersion = ariesConfig.getString("version")
  val baseUrl = s"$url/$apiVersion"

  object commandCredentials {
    val username = ariesConfig.getString("command-user-name")
    val password = ariesConfig.getString("command-user-password")
  }

  object searchCredentials {
    val username = ariesConfig.getString("search-user-name")
    val password = ariesConfig.getString("search-user-password")
  }

  val requestRetryCount = ariesConfig.getInt("request-retry-count")

}

object AriesSettings extends ExtensionId[AriesSettings] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): AriesSettings = new AriesSettings(system.settings.config)

  override def lookup(): ExtensionId[_ <: Extension] = AriesSettings
}
