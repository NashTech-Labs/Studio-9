package aries.rest.heartbeat

import java.util.{ Date, UUID }

import akka.actor.ActorRef
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import aries.common.json4s.AriesJson4sSupport
import aries.common.rest.BaseConfig
import aries.common.rest.routes.{ CRUDRoutes, HttpEndpoint, Translator }
import aries.domain.rest.datacontracts.heartbeat.HeartbeatDataContract
import aries.rest.common.CustomUnmarshallers
import aries.domain.service.heartbeat.{ FindLatestHeartbeat, HeartbeatEntity, HeartbeatSearchCriteria }

import scala.language.postfixOps

object HeartbeatSearchHttpEndpoint {

  def apply(heartbeatCommandService: ActorRef, config: BaseConfig): HeartbeatSearchHttpEndpoint = {
    new HeartbeatSearchHttpEndpoint(heartbeatCommandService: ActorRef, config: BaseConfig)
  }

  implicit val toHeartbeatSearchCriteria = new Translator[HeartbeatDataContract.SearchRequest, HeartbeatSearchCriteria] {
    def translate(from: HeartbeatDataContract.SearchRequest): HeartbeatSearchCriteria = {
      HeartbeatSearchCriteria(
        job_id = from.jobId
      )
    }
  }

}

class HeartbeatSearchHttpEndpoint(heartbeatQueryService: ActorRef, val config: BaseConfig) extends HttpEndpoint
    with CustomUnmarshallers
    with CRUDRoutes
    with AriesJson4sSupport {

  import HeartbeatHttpEndpoint._
  import HeartbeatSearchHttpEndpoint._

  val routes: Route = {
    path("heartbeats" / "latest") {
      get {
        parameters('jobId.as[UUID]) { (jobId) =>
          doLatestSearch(jobId)
        }
      }
    }
  }

  private[this] def doLatestSearch(jobId: UUID): Route = {
    onSuccess((heartbeatQueryService ? FindLatestHeartbeat(jobId)).mapTo[Seq[HeartbeatEntity]]) { heartbeats =>
      respond(heartbeats)
    }
  }
}
