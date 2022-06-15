package aries.service.heartbeat

import akka.actor.Props
import akka.pattern.pipe
import aries.common.service.{ DateSupport, NamedActor, Service, UUIDSupport }
import aries.domain.service.CreateEntity
import aries.domain.service.heartbeat.{ CreateHeartbeat, HeartbeatEntity }

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }

object HeartbeatCommandService extends NamedActor {
  override val Name = "heartbeat-command-service"
  def props()(implicit ec: ExecutionContext): Props = {
    Props(new HeartbeatCommandService())
  }
}

class HeartbeatCommandService(implicit ec: ExecutionContext) extends Service
    with UUIDSupport
    with DateSupport {

  val repository = HeartbeatRepository(context.system)

  log.debug(s"HeartbeatCommandService is starting at {}", self.path)

  def receive: Receive = {

    case CreateEntity(createHeartbeat: CreateHeartbeat) => {
      def create(): Future[HeartbeatEntity] = {
        val entity =
          HeartbeatEntity(
            id                       = java.util.UUID.randomUUID(),
            job_id                   = createHeartbeat.job_id,
            created_at               = createHeartbeat.created_at,
            current_progress         = createHeartbeat.current_progress,
            estimated_time_remaining = createHeartbeat.estimated_time_remaining
          )
        repository.create(entity)
      }

      log.info("[ JobId: {} ] - [Create Heartbeat] - Creating Heartbeat in ElasticSearch.", createHeartbeat.job_id)

      val result = create()

      result.onComplete {
        case Success(_) => log.info("[ JobId: {} ] - [Create Heartbeat] - Heartbeat creation succeeded.", createHeartbeat.job_id)
        case Failure(e) => log.error("[ JobId: {} ] - [Create Heartbeat] - Failed to create Heartbeat [{}] with error: {}", createHeartbeat.job_id, createHeartbeat, e)
      }

      result pipeTo sender

    }
  }
}
