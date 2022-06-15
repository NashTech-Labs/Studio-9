package aries.service.job

import java.util.UUID

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import aries.common.elastic.{ ElasticSearchClient, ElasticSearchRepository, ElasticSearchSettings }
import aries.common.json4s.AriesJson4sSupport
import aries.domain.service.job.JobEntity
import com.sksamuel.elastic4s.http.HttpClient

import scala.concurrent.ExecutionContext
import scala.reflect.ManifestFactory

trait JobRepository extends ElasticSearchRepository[UUID, JobEntity] with AriesJson4sSupport

object JobRepository {
  def apply(system: ActorSystem): JobRepository = new JobRepository {
    val settings = ElasticSearchSettings(system)

    override val elasticClient: HttpClient = ElasticSearchClient(system).httpClient
    override val logger: LoggingAdapter = system.log

    override implicit val ec: ExecutionContext = system.dispatcher
    override implicit val documentManifest: Manifest[JobEntity] = ManifestFactory.classType(classOf[JobEntity])

    override val index: String = settings.job_index
    override val indexType: String = settings.job_type
  }
}
