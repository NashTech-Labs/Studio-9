package cortex.rest.job

import akka.actor.{ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider}
import com.typesafe.config.Config

class CortexSettings(config: Config) extends Extension {
  private val cortexConfig = config.getConfig("cortex")

  val url = cortexConfig.getString("rest-url")
  val apiVersion = cortexConfig.getString("version")
  val baseUrl = s"$url/$apiVersion"

  object credentials {
    val username = cortexConfig.getString("username")
    val password = cortexConfig.getString("password")
  }

}

object CortexSettings extends ExtensionId[CortexSettings] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): CortexSettings = new CortexSettings(system.settings.config)

  override def lookup(): ExtensionId[_ <: Extension] = CortexSettings
}
