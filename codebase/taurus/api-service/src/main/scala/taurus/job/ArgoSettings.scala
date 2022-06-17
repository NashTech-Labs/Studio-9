package taurus.job

import akka.actor.{ ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider }
import com.typesafe.config.Config

class ArgoSettings(config: Config) extends Extension {
  private val argoConfig = config.getConfig("argo")
  val serviceName = "online-prediction"

  val host = argoConfig.getString("host")
  val port = argoConfig.getString("port")
  def streamSettingName(streamId: String): String = "stream_" + streamId

  private val credentialsConf = argoConfig.getConfig("credentials")
  val username = credentialsConf.getString("username")
  val password = credentialsConf.getString("password")

  val argoResponseTimeout = argoConfig.getDuration("response-timeout")

  val apiVersion = argoConfig.getString("api-version")
  def streamSettingsUrl(streamId: String): String =
    s"http://$host:$port/$apiVersion/services/$serviceName/config-settings/${streamSettingName(streamId)}"

}

object ArgoSettings extends ExtensionId[ArgoSettings] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): ArgoSettings = new ArgoSettings(system.settings.config)

  override def lookup(): ExtensionId[_ <: Extension] = ArgoSettings
}
