package cortex.rest.job

import java.util.UUID

import akka.actor.ActorRef
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import cortex.common.json4s.CortexJson4sSupport
import cortex.common.rest.BaseConfig
import cortex.common.rest.routes.{ HttpEndpoint, Translator }
import cortex.domain.rest.job.JobSearchCriteriaContract
import cortex.domain.service.job._
import cortex.rest.common.CustomUnmarshallers

import scala.language.postfixOps

object JobSearchHttpEndpoint {

  def apply(jobCommandService: ActorRef, config: BaseConfig): JobSearchHttpEndpoint = {
    new JobSearchHttpEndpoint(jobCommandService: ActorRef, config: BaseConfig)
  }

  implicit val toJobSearchCriteria = new Translator[JobSearchCriteriaContract, JobSearchCriteria] {
    def translate(from: JobSearchCriteriaContract): JobSearchCriteria = {
      JobSearchCriteria(
        owner   = from.owner,
        jobType = from.jobType,
        status  = from.status
      )
    }
  }

}

class JobSearchHttpEndpoint(jobQueryService: ActorRef, val config: BaseConfig) extends HttpEndpoint
    with CustomUnmarshallers
    with CortexJson4sSupport {

  import JobHttpEndpoint._
  import JobSearchHttpEndpoint._

  val routes: Route =
    pathPrefix("jobs" / "search") {
      pathEndOrSingleSlash {
        get {
          parameters('owner.as[UUID].?, 'type?, 'status.as[JobStatus].?) { (owner, jobType, status) =>
            val request = JobSearchCriteriaContract(owner, jobType, status)
            doSearch(request)
          }
        } ~
          post {
            entity(as[JobSearchCriteriaContract]) { request =>
              doSearch(request)
            }
          }
      }
    }

  private[this] def doSearch(searchCriteria: JobSearchCriteriaContract): Route = {
    onSuccess((jobQueryService ? FindJob(searchCriteria)).mapTo[Seq[JobEntity]]) { jobs =>
      respond(jobs)
    }
  }
}

