package aries.rest.job

import java.util.{ Date, UUID }

import akka.actor.ActorRef
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import aries.common.json4s.AriesJson4sSupport
import aries.common.rest.BaseConfig
import aries.common.rest.routes.{ CRUDRoutes, HttpEndpoint, Translator }
import aries.rest.common.CustomUnmarshallers
import aries.domain.rest.datacontracts.job.JobDataContract
import aries.domain.service.job._
import aries.domain.service.job.FindJob

import scala.language.postfixOps

object JobSearchHttpEndpoint {

  def apply(jobCommandService: ActorRef, config: BaseConfig): JobSearchHttpEndpoint = {
    new JobSearchHttpEndpoint(jobCommandService: ActorRef, config: BaseConfig)
  }

  implicit val toJobSearchCriteria = new Translator[JobDataContract.SearchRequest, JobSearchCriteria] {
    def translate(from: JobDataContract.SearchRequest): JobSearchCriteria = {
      JobSearchCriteria(
        owner    = from.owner,
        job_type = from.jobType,
        status   = from.status
      )
    }
  }

}

class JobSearchHttpEndpoint(jobQueryService: ActorRef, val config: BaseConfig) extends HttpEndpoint
    with CustomUnmarshallers
    with CRUDRoutes
    with AriesJson4sSupport {

  import JobHttpEndpoint._
  import JobSearchHttpEndpoint._

  val routes: Route = {
    pathPrefix("jobs" / "search") {
      pathEndOrSingleSlash {
        get {
          parameters('owner.as[UUID].?, 'jobType.?, 'status.as[JobStatus].?) { (owner, jobType, status) =>
            val request = JobDataContract.SearchRequest(owner, jobType, status)
            doSearch(request)
          }
        } ~
          post {
            entity(as[JobDataContract.SearchRequest]) { request =>
              doSearch(request)
            }
          }
      }
    } ~
      pathPrefix("jobs") {
        retrieve(jobQueryService) ~
          list(jobQueryService)
      }
  }

  private[this] def doSearch(searchCriteria: JobDataContract.SearchRequest): Route = {
    onSuccess((jobQueryService ? FindJob(searchCriteria)).mapTo[Seq[JobEntity]]) { jobs =>
      respond(jobs)
    }
  }
}

