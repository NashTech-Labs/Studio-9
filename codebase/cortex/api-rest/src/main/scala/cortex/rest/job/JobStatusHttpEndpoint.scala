package cortex.rest.job

import akka.actor.ActorRef
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import cortex.common.json4s.CortexJson4sSupport
import cortex.common.rest.BaseConfig
import cortex.common.rest.routes.{ HttpEndpoint, Translator }
import cortex.domain.rest.job.{ CortexErrorDetailsContract, JobStatusDataContract }
import cortex.domain.service.job._

import scala.language.postfixOps

object JobStatusHttpEndpoint {

  def apply(jobQueryService: ActorRef, config: BaseConfig): JobStatusHttpEndpoint = {
    new JobStatusHttpEndpoint(jobQueryService: ActorRef, config: BaseConfig)
  }

  val toCortexErrorDetailsContract = new Translator[CortexErrorDetails, CortexErrorDetailsContract] {
    def translate(from: CortexErrorDetails): CortexErrorDetailsContract = {
      CortexErrorDetailsContract(
        errorCode     = from.errorCode,
        errorMessages = from.errorMessages,
        errorDetails  = from.errorDetails
      )
    }
  }

  implicit val toJobStatusDataContract = new Translator[JobStatusData, JobStatusDataContract] {
    def translate(from: JobStatusData): JobStatusDataContract = {
      JobStatusDataContract(
        status                 = from.status,
        currentProgress        = from.currentProgress,
        estimatedTimeRemaining = from.estimatedTimeRemaining,
        cortexErrorDetails     = from.cortexErrorDetails.map(toCortexErrorDetailsContract.translate)
      )
    }
  }

}

class JobStatusHttpEndpoint(jobQueryService: ActorRef, val config: BaseConfig) extends HttpEndpoint with CortexJson4sSupport {

  import JobStatusHttpEndpoint._

  val routes: Route =
    pathPrefix("jobs" / JavaUUID / "status") { jobId =>
      pathEndOrSingleSlash {
        get {
          onSuccess((jobQueryService ? GetJobStatus(jobId)).mapTo[Option[JobStatusData]]) {
            case Some(jobStatus) => respond(jobStatus)
            case None            => respond(StatusCodes.NotFound)
          }
        }
      }
    }

}

