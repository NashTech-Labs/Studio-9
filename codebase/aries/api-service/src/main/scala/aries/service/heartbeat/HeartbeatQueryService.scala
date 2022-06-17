package aries.service.heartbeat

import java.util.UUID

import akka.actor.Props
import akka.pattern.pipe
import aries.common.service.{ DateSupport, NamedActor, Service, UUIDSupport }
import aries.domain.service.heartbeat.{ FindLatestHeartbeat, HeartbeatEntity }
import com.sksamuel.elastic4s.http.ElasticDsl._
import com.sksamuel.elastic4s.searches.queries.QueryDefinition
import com.sksamuel.elastic4s.searches.sort.SortOrder

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }

object HeartbeatQueryService extends NamedActor {
  override val Name = "heartbeat-query-service"
  def props()(implicit ec: ExecutionContext): Props = {
    Props(new HeartbeatQueryService())
  }

}

class HeartbeatQueryService(implicit ec: ExecutionContext) extends Service
    with UUIDSupport
    with DateSupport {

  val repository = HeartbeatRepository(context.system)

  log.debug(s"HeartbeatQueryService is starting at {}", self.path)

  def receive: Receive = {

    case FindLatestHeartbeat(jobId) => {
      def buildQuery(): Iterable[QueryDefinition] = {
        val queryDef = Iterable(matchQuery("job_id", jobId.toString))
        queryDef
      }

      def findBy(jobId: UUID): Future[Seq[HeartbeatEntity]] = {
        log.info("[Find Heartbeats] - Retrieving Jobs from ElasticSearch.")

        repository.search(buildQuery, "created_at", SortOrder.DESC, 1) andThen {
          case Success(_) => log.info("[Find Heartbeats] - Heartbeat finding succeeded")
          case Failure(e) => log.error("[Find Heartbeats] - Failed to find Heartbeats for Job: [{}] with error: [{}]", jobId, e)
        }
      }

      findBy(jobId) pipeTo sender
    }
  }
}
