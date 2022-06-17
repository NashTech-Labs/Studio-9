package aries.rest.heartbeat

import akka.actor.ActorRef
import akka.http.scaladsl.server.Route
import aries.common.json4s.AriesJson4sSupport
import aries.common.rest.BaseConfig
import aries.common.rest.routes.{ CRUDRoutes, HttpEndpoint, Translator }
import aries.domain.rest.datacontracts.heartbeat.HeartbeatDataContract
import aries.domain.service.heartbeat.{ CreateHeartbeat, HeartbeatEntity }

object HeartbeatHttpEndpoint {

  def apply(heartbeatCommandService: ActorRef, heartbeatQueryService: ActorRef, config: BaseConfig): HeartbeatHttpEndpoint = {
    new HeartbeatHttpEndpoint(heartbeatCommandService: ActorRef, heartbeatQueryService: ActorRef, config: BaseConfig)
  }

  implicit val toCreateHeartbeat = new Translator[HeartbeatDataContract.CreateRequest, CreateHeartbeat] {
    def translate(from: HeartbeatDataContract.CreateRequest): CreateHeartbeat = {
      CreateHeartbeat(
        job_id                   = from.jobId,
        created_at               = from.created,
        current_progress         = from.currentProgress,
        estimated_time_remaining = from.estimatedTimeRemaining
      )
    }
  }

  implicit val toHeartbeatDataContract = new Translator[HeartbeatEntity, HeartbeatDataContract.Response] {
    def translate(from: HeartbeatEntity): HeartbeatDataContract.Response = {
      HeartbeatDataContract.Response(
        id                     = from.id,
        jobId                  = from.job_id,
        created                = from.created_at,
        currentProgress        = from.current_progress,
        estimatedTimeRemaining = from.estimated_time_remaining
      )
    }
  }

}

class HeartbeatHttpEndpoint(heartbeatCommandService: ActorRef, heartbeatQueryService: ActorRef, val config: BaseConfig) extends HttpEndpoint
    with AriesJson4sSupport
    with CRUDRoutes {

  import HeartbeatHttpEndpoint._

  val routes: Route =
    pathPrefix("heartbeats") {
      create(heartbeatCommandService)
    }

}