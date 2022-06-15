package aries.service.job

import java.util.UUID

import akka.actor.Props
import akka.pattern.pipe
import aries.common.service.{ DateSupport, NamedActor, Service, UUIDSupport }
import aries.domain.rest.datacontracts.job.JobDataContract
import aries.domain.service.job.{ JobEntity, JobSearchCriteria }
import aries.domain.service.job.FindJob
import aries.domain.service.{ ListEntities, RetrieveEntity }
import com.sksamuel.elastic4s.http.ElasticDsl.matchQuery
import com.sksamuel.elastic4s.searches.queries.QueryDefinition
import com.sksamuel.elastic4s.searches.sort.SortOrder

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }

object JobQueryService extends NamedActor {
  override val Name = "job-query-service"

  def props()(implicit ec: ExecutionContext): Props = {
    Props(new JobQueryService())
  }

}

class JobQueryService(implicit ec: ExecutionContext) extends Service
    with UUIDSupport
    with DateSupport {

  log.debug("JobQueryService is starting at {}", self.path)

  val repository = JobRepository(context.system)

  def receive: Receive = {

    case RetrieveEntity(id: UUID) => {
      def getById(id: UUID): Future[Option[JobEntity]] = {
        log.info("[ JobId: {} ] - [Retrieve Job] - Retrieving Job from ElasticSearch.", id)
        repository.retrieve(id) andThen {
          case Success(Some(_)) => log.info("[ JobId: {} ] - [Retrieve Job] - Job retrieval succeeded.", id)
          case Success(None)    => log.warning("[ JobId: {} ] - [Retrieve Job] - Tried to retrieve Job but it was not found.", id)
          case Failure(e)       => log.error("[ JobId: {} ] - [Retrieve Job] - Failed to retrieve Job with error: {}", id, e)
        }
      }

      getById(id) pipeTo sender
    }

    case ListEntities => {
      def list(): Future[Seq[JobEntity]] = {
        log.info("[List Jobs] - Retrieving Jobs from ElasticSearch.")
        repository.list() andThen {
          case Success(_) => log.info("[List Jobs] - Job listing succeeded")
          case Failure(e) => log.error("[List Jobs] - Failed to list Jobs with error: [{}]", e)
        }
      }

      list() pipeTo sender
    }

    case FindJob(criteria) => {
      def buildQuery(): Iterable[QueryDefinition] = {
        def getJobParams(job: JobSearchCriteria): Map[String, Option[Any]] = {
          val values = job.productIterator
          job.getClass.getDeclaredFields.map(_.getName -> values.next.asInstanceOf[Option[Any]]).toMap
        }

        val criteriaMap = getJobParams(criteria)
        val queryDef = for (x <- criteriaMap; if x._2.isDefined) yield matchQuery(x._1, x._2.get.toString.toLowerCase)
        queryDef
      }

      def findBy(criteria: JobSearchCriteria): Future[Seq[JobEntity]] = {
        log.info("[Find Jobs] - Retrieving Jobs from ElasticSearch.")

        repository.search(buildQuery, "id", SortOrder.ASC, -1) andThen {
          case Success(_) => log.info("[Find Jobs] - Job finding succeeded")
          case Failure(e) => log.error("[Find Jobs] - Failed to find Jobs using Criteria [{}] with error: [{}]", criteria, e)
        }
      }

      findBy(criteria) pipeTo sender
    }
  }

}
