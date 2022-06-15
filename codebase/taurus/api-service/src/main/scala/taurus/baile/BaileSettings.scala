package taurus.baile

import akka.actor.{ ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider }
import com.typesafe.config.Config

class BaileSettings(config: Config) extends Extension {
  private val baileConfig = config.getConfig("baile")

  val url = baileConfig.getString("rest-url")
  val apiVersion = baileConfig.getString("api-version")
  val baseUrl = s"$url/$apiVersion"
  val requestRetryCount = baileConfig.getInt("request-retry-count")

  object credentials {
    val username = baileConfig.getString("credentials.username")
    val password = baileConfig.getString("credentials.password")
  }

}

object BaileSettings extends ExtensionId[BaileSettings] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): BaileSettings = new BaileSettings(system.settings.config)

  override def lookup(): ExtensionId[_ <: Extension] = BaileSettings
}
