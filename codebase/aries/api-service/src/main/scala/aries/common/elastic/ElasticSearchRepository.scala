package aries.common.elastic

import akka.event.LoggingAdapter
import aries.common.json4s.Json4sSupport
import aries.domain.service.DomainEntity
import com.sksamuel.elastic4s.http.ElasticDsl._
import com.sksamuel.elastic4s.http.{ ElasticDsl, HttpClient }
import com.sksamuel.elastic4s.json4s.ElasticJson4s.Implicits._
import com.sksamuel.elastic4s.searches.queries.QueryDefinition
import com.sksamuel.elastic4s.searches.sort.SortOrder
import com.sksamuel.elastic4s.{ IndexAndType, RefreshPolicy }

import scala.concurrent.{ ExecutionContext, Future }

trait ElasticSearchRepository[K, T <: DomainEntity[K]] { self: Json4sSupport =>

  val elasticClient: HttpClient
  val logger: LoggingAdapter

  implicit val ec: ExecutionContext
  implicit val documentManifest: Manifest[T]

  val index: String
  val indexType: String

  private def indexAndType: IndexAndType = IndexAndType(index, indexType)

  def create(document: T): Future[T] = withRetry() {
    elasticClient.execute {
      indexInto(indexAndType) id document.id doc document refresh RefreshPolicy.Immediate
    }.flatMap {
      // TODO: should we do an upsert here or fail if the trying to create an already existent document?
      case response if (response.result == "created" || response.result == "updated") => Future.successful(document)
      case other => Future.failed(new Exception(s"Fail to create document in ElasticSearch with response: [${other}]"))
    }
  }

  def retrieve(id: K): Future[Option[T]] = withRetry() {
    elasticClient.execute {
      get(id) from indexAndType
    }.map(x => if (x.exists) x.to[Option[T]] else None)
  }

  def update(id: K, document: T): Future[Option[T]] = withRetry() {
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
  }

  def list(): Future[Seq[T]] = withRetry() {
    elasticClient.execute {
      ElasticDsl.search(indexAndType) query matchAllQuery()
    }.map(_.to[T])
  }

  def search(queryDef: Iterable[QueryDefinition], sortDef: String, sortOrder: SortOrder, numResults: Int): Future[Seq[T]] = withRetry() {
    elasticClient.execute {
      ElasticDsl.search(indexAndType) query {
        boolQuery().must(queryDef)
      } sortBy {
        fieldSort(sortDef) order sortOrder
      } size numResults
    }.map(_.to[T])
  }

  // TODO: should we perform the retry at this level?
  // Change to something using a backoff policy, like Atmos: https://github.com/zmanio/atmos
  // Take a look at: https://sentrana.atlassian.net/browse/COR-184
  private def withRetry[A](retries: Int = 3)(f: => Future[A]): Future[A] = {
    f.recoverWith {
      case _ if retries > 0 => {
        logger.warning("Call to ElasticSearch failed and needs to be retried. Retries left: {}", retries)
        withRetry(retries - 1)(f)
      }
    }
  }

}
