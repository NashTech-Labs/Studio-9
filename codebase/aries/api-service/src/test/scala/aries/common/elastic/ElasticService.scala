package aries.common.elastic

import java.util.UUID

import aries.common.json4s.AriesJson4sSupport
import aries.domain.service.heartbeat.{ CreateHeartbeat, HeartbeatEntity }
import aries.domain.service.job.JobStatus.Cancelled
import aries.domain.service.job._
import com.sksamuel.elastic4s.http.ElasticDsl._
import com.sksamuel.elastic4s.http.HttpClient
import com.sksamuel.elastic4s.json4s.ElasticJson4s.Implicits._
import com.sksamuel.elastic4s.searches.queries.QueryDefinition
import com.sksamuel.elastic4s.{ IndexAndType, RefreshPolicy }
import org.slf4j.{ Logger, LoggerFactory }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait ElasticService extends AriesJson4sSupport {

  /*val logger: Logger = LoggerFactory.getLogger("aries.elastic.logs")

  val index: String
  val indexType: String
  val elasticClient: HttpClient

  val numRetries: Int = 3

  def indexAndType: IndexAndType = IndexAndType(index, indexType)

  def doIndexInto(job: CreateJobData): Future[Option[JobEntity]] = {
    withRetry(numRetries) {
      elasticClient.execute {
        indexInto(indexAndType) id job.id doc job refresh RefreshPolicy.Immediate
      }
    }.flatMap(x => if (x.result == "created" || x.result == "updated") getById(job.id) else Future(None))
  }

  def doHeartbeatIndexInto(heartbeat: CreateHeartbeat): Future[Option[HeartbeatEntity]] = {
    withRetry(numRetries) {
      elasticClient.execute {
        indexInto(indexAndType) id heartbeat.id doc heartbeat refresh RefreshPolicy.Immediate
      }
    }.flatMap(x => if (x.result == "created" || x.result == "updated") getHeartbeatById(heartbeat.id) else Future(None))
  }

  def getHeartbeatById(entityId: UUID): Future[Option[HeartbeatEntity]] = {
    withRetry(numRetries) {
      elasticClient.execute {
        get(entityId) from indexAndType
      }
    }.map(x => if (x.exists) x.to[Option[HeartbeatEntity]] else None)
  }

  def getById(entityId: UUID): Future[Option[JobEntity]] = {
    withRetry(numRetries) {
      elasticClient.execute {
        get(entityId) from indexAndType
      }
    }.map(x => if (x.exists) x.to[Option[JobEntity]] else None)
  }

  def doUpdate(entityId: UUID, job: UpdateJobData): Future[Option[JobEntity]] = {
    withRetry(numRetries) {
      elasticClient.execute {
        update(entityId).in(indexAndType).doc(
          job
        ) refresh RefreshPolicy.Immediate
      }
    }.flatMap(x => if (x.result == "updated") getById(entityId) else Future(None))
  }

  def doDeleteById(entityId: UUID): Future[Option[JobEntity]] = {
    val job = UpdateJobData(status = Option(Cancelled))
    doUpdate(entityId, job)
  }

  def doSearch(criteria: JobSearchCriteria): Future[Seq[JobEntity]] = {
    val criteriaMap = getJobParams(criteria)
    val queryDef: Iterable[QueryDefinition] = for (x <- criteriaMap; if x._2.isDefined) yield matchQuery(x._1, x._2.get.toString.toLowerCase)
    withRetry(numRetries) {
      elasticClient.execute {
        search(indexAndType) query {
          boolQuery().must(queryDef)
        }
      }
    }.map(_.to[JobEntity])
  }

  def listAll(): Future[Seq[JobEntity]] = {
    withRetry(numRetries) {
      elasticClient.execute {
        search(indexAndType) query matchAllQuery()
      }
    }.map(_.to[JobEntity])
  }

  def getJobParams(job: JobSearchCriteria): Map[String, Option[Any]] = {
    val values = job.productIterator
    job.getClass.getDeclaredFields.map(_.getName -> values.next.asInstanceOf[Option[Any]]).toMap
  }

  def withRetry[T](retries: Int = 3)(f: => Future[T]): Future[T] = {
    f.recoverWith {
      case _ if retries > 0 => {
        logger.warn(s"Call to ElasticSearch failed and needs to be retried. Retries left: $retries")
        withRetry(retries - 1)(f)
      }
    }
  }
*/
}

