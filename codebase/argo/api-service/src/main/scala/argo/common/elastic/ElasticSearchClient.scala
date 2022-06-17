package argo.common.elastic

import akka.actor.{ ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider }
import com.sksamuel.elastic4s.ElasticsearchClientUri
import com.sksamuel.elastic4s.http.HttpClient
import com.typesafe.config.Config

trait ElasticSearchClient extends Extension {

  val httpClient: HttpClient

}

object ElasticSearchClient extends ExtensionId[ElasticSearchClient] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): ElasticSearchClient = new ElasticSearchClient {
    override val httpClient = {
      val config = ElasticSearchSettings(system)

      val user = config.user
      val password = config.password
      val host = config.host
      val port = config.port
      val useSSL = config.useSSL
      val timeout = config.timeout

      HttpClient(ElasticsearchClientUri(s"elasticsearch://$host:$port?ssl=$useSSL"))
    }
  }

  override def lookup(): ExtensionId[_ <: Extension] = ElasticSearchClient
}

// Settings
class ElasticSearchSettings(config: Config) extends Extension {
  private val elasticConfig = config.getConfig("elastic-search")

  val user = elasticConfig.getString("user")
  val password = elasticConfig.getString("password")
  val host = elasticConfig.getString("host")
  val port = elasticConfig.getInt("port")
  val useAuth = elasticConfig.getBoolean("useAuth")
  val useSSL = elasticConfig.getBoolean("useSSL")
  val timeout = elasticConfig.getInt("timeout")

}

object ElasticSearchSettings extends ExtensionId[ElasticSearchSettings] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): ElasticSearchSettings = new ElasticSearchSettings(system.settings.config)

  override def lookup(): ExtensionId[_ <: Extension] = ElasticSearchSettings
}