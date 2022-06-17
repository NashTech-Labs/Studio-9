package argo.service.config

import java.util.UUID

import akka.actor.{ ActorSystem, ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider }
import akka.event.LoggingAdapter
import argo.common.elastic.{ ElasticSearchClient, ElasticSearchRepository }
import argo.common.json4s.ArgoJson4sSupport
import argo.domain.service.config.ConfigSetting
import com.sksamuel.elastic4s.http.HttpClient
import com.typesafe.config.Config

import scala.concurrent.ExecutionContext
import scala.reflect.ManifestFactory

trait ConfigSettingRepository extends ElasticSearchRepository[UUID, ConfigSetting] with ArgoJson4sSupport

object ConfigSettingRepository {
  def apply(system: ActorSystem): ConfigSettingRepository = new ConfigSettingRepository {
    val settings = ConfigSettingRepositorySettings(system)

    override val elasticClient: HttpClient = ElasticSearchClient(system).httpClient
    override val logger: LoggingAdapter = system.log

    override implicit val ec: ExecutionContext = system.dispatcher
    override implicit val documentManifest: Manifest[ConfigSetting] = ManifestFactory.classType(classOf[ConfigSetting])

    override val index: String = settings.indexName
    override val indexType: String = settings.indexType
  }
}

// Settings
class ConfigSettingRepositorySettings(config: Config) extends Extension {
  private val elasticConfig = config.getConfig("elastic-search")
  val configSettingIndexConfig = elasticConfig.getConfig("indexes.config-setting")

  val indexName = configSettingIndexConfig.getString("name")
  val indexType = configSettingIndexConfig.getString("type")

}

object ConfigSettingRepositorySettings extends ExtensionId[ConfigSettingRepositorySettings] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): ConfigSettingRepositorySettings = new ConfigSettingRepositorySettings(system.settings.config)

  override def lookup(): ExtensionId[_ <: Extension] = ConfigSettingRepositorySettings
}
