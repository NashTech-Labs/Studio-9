package argo.common.elastic

import java.net.{ ConnectException, UnknownHostException }

import akka.event.LoggingAdapter
import argo.common.json4s.Json4sSupport
import argo.domain.service.DomainEntity
import com.sksamuel.elastic4s.http.ElasticDsl._
import com.sksamuel.elastic4s.http.delete.DeleteResponse
import com.sksamuel.elastic4s.http.{ ElasticDsl, HttpClient }
import com.sksamuel.elastic4s.json4s.ElasticJson4s.Implicits._
import com.sksamuel.elastic4s.searches.queries.QueryDefinition
import com.sksamuel.elastic4s.searches.sort.SortOrder
import com.sksamuel.elastic4s.{ IndexAndType, RefreshPolicy }

import scala.concurrent.{ ExecutionContext, Future }

object ElasticSearchRepository {
  val LimitMaxValue = 10000

  case class DeleteResult(deleted: Boolean)
  case class ElasticSearchRepositoryException(msg: String, cause: Option[Throwable] = None) extends Exception(msg) {
    cause.foreach(initCause(_))
  }

  implicit class ExtendedFuture[T](future: => Future[T]) {
    // TODO: should we perform the retry at this level? Or at services layer?
    // Change to something using a backoff policy, like Atmos: https://github.com/zmanio/atmos
    // Take a look at: https://sentrana.atlassian.net/browse/COR-184
    def withRetry(retries: Int = 3)(implicit ec: ExecutionContext, logger: LoggingAdapter): Future[T] = {
      future.recoverWith {
        case _ if retries > 0 => {
          logger.warning("Call to ElasticSearch failed and needs to be retried. Retries left: {}", retries)
          withRetry(retries - 1)
        }
      }
    }

    def withDefaultErrorHandling()(implicit ec: ExecutionContext): Future[T] = {
      // If it is an ElasticSearchRepositoryException just return it,
      // if it is not, wrap it in a ElasticSearchRepositoryException
      future.recoverWith {
        case e: ElasticSearchRepositoryException => Future.failed(e)

        case e: UnknownHostException =>
          Future.failed(ElasticSearchRepositoryException("Error connecting to elasticsearch. Could not find provided host.", Some(e)))

        case e: ConnectException =>
          Future.failed(ElasticSearchRepositoryException("Error connecting to elasticsearch. Connection refused at the provided host.", Some(e)))
      }
    }
  }
}

trait ElasticSearchRepository[K, T <: DomainEntity[K]] { self: Json4sSupport =>
  import ElasticSearchRepository._

  val elasticClient: HttpClient

  implicit val ec: ExecutionContext
  implicit val logger: LoggingAdapter

  implicit val documentManifest: Manifest[T]

  val index: String
  val indexType: String

  private def indexAndType: IndexAndType = IndexAndType(index, indexType)

  def create(document: T): Future[T] = {
    elasticClient.execute {
      indexInto(indexAndType) id document.id doc document refresh RefreshPolicy.Immediate
    }.flatMap {
      // TODO: should we do an upsert here or fail if the trying to create an already existent document?
      case response if (response.result == "created" || response.result == "updated") => Future.successful(document)
      case other => Future.failed(ElasticSearchRepositoryException(s"Fail to create document in ElasticSearch with response: [${other}]"))
    }
  }.withRetry().withDefaultErrorHandling()

  def retrieve(id: K): Future[Option[T]] = {
    elasticClient.execute {
      get(id) from indexAndType
    }.map(x => if (x.exists) x.to[Option[T]] else None)
  }.withRetry().withDefaultErrorHandling()

  def update(id: K, document: T): Future[Option[T]] = {
    // TODO: verify what happens if trying to update a non-existent document at this point. Does it get updated/created anyway?
    // https://www.elastic.co/guide/en/elasticsearch/reference/5.5/docs-update.html#_updates_with_a_partial_document
    elasticClient.execute {
      ElasticDsl.update(id).in(indexAndType).doc(
        document
      ) refresh RefreshPolicy.Immediate
    }.flatMap {
      // Note: https://www.elastic.co/guide/en/elasticsearch/reference/5.5/docs-update.html#_detecting_noop_updates
      case x if (x.result == "updated" || x.result == "noop") => Future.successful(Some(document))
      case _ => Future.successful(None)
    }
  }.withRetry().withDefaultErrorHandling()

  def delete(id: K): Future[DeleteResult] = {
    elasticClient.execute {
      ElasticDsl.delete(id) from indexAndType
    } flatMap {
      case DeleteResponse(_, true, _, _, _, _, "deleted") => Future.successful(DeleteResult(true))
      case DeleteResponse(_, false, _, _, _, _, _) => Future.successful(DeleteResult(false))
      case _ => Future.failed(ElasticSearchRepositoryException(s"Unexpected response while deleting document with id $id from index and type $indexAndType"))
    }
  }.withRetry().withDefaultErrorHandling()

  def list(): Future[Seq[T]] = {
    elasticClient.execute {
      ElasticDsl.search(indexAndType) query matchAllQuery()
    }.map(_.to[T])
  }.withRetry().withDefaultErrorHandling()

  def search(queries: Seq[QueryDefinition], sortField: String, sortOrder: SortOrder, limit: Int): Future[Seq[T]] = {
    elasticClient.execute {
      ElasticDsl.search(indexAndType) query {
        boolQuery must queries
      } sortBy {
        fieldSort(sortField) order sortOrder
      } limit limit
    }.map(_.to[T])
  }.withRetry().withDefaultErrorHandling()

}
