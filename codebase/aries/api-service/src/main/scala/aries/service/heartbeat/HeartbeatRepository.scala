package aries.service.heartbeat

import java.util.UUID

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import aries.common.elastic.{ ElasticSearchClient, ElasticSearchRepository, ElasticSearchSettings }
import aries.common.json4s.AriesJson4sSupport
import aries.domain.service.heartbeat.HeartbeatEntity
import com.sksamuel.elastic4s.http.HttpClient

import scala.reflect.ManifestFactory

trait HeartbeatRepository extends ElasticSearchRepository[UUID, HeartbeatEntity] with AriesJson4sSupport

object HeartbeatRepository {

  def apply(system: ActorSystem): HeartbeatRepository = new HeartbeatRepository {
    val settings = ElasticSearchSettings(system)

    override val elasticClient: HttpClient = ElasticSearchClient(system).httpClient
    override implicit val ec = system.dispatcher

    override implicit val documentManifest: Manifest[HeartbeatEntity] = ManifestFactory.classType(classOf[HeartbeatEntity])
    override val logger: LoggingAdapter = system.log

    override val index: String = settings.heartbeat_index
    override val indexType: String = settings.heartbeat_type
  }

}
