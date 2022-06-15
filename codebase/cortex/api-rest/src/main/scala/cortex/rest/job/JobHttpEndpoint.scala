package cortex.rest.job

import akka.actor.ActorRef
import akka.http.scaladsl.server.Route
import cortex.api.job.message.{ TaskTimeInfo, TimeInfo }
import cortex.common.json4s.CortexJson4sSupport
import cortex.common.rest.BaseConfig
import cortex.common.rest.routes.{ CRUDRoutes, HttpEndpoint, Translator }
import cortex.domain.rest.job.{ JobEntityContract, SubmitJobDataContract, TaskTimeInfoContract, TimeInfoContract }
import cortex.domain.service.job.{ JobEntity, SubmitJobData }

object JobHttpEndpoint {

  def apply(jobCommandService: ActorRef, jobQueryService: ActorRef, config: BaseConfig): JobHttpEndpoint = {
    new JobHttpEndpoint(jobCommandService: ActorRef, jobQueryService: ActorRef, config: BaseConfig)
  }

  implicit val toJobData = new Translator[SubmitJobDataContract, SubmitJobData] {
    def translate(from: SubmitJobDataContract): SubmitJobData = {
      SubmitJobData(
        id        = from.id,
        owner     = from.owner,
        jobType   = from.jobType,
        inputPath = from.inputPath
      )
    }
  }

  val toTimeInfoContract = new Translator[TimeInfo, TimeInfoContract] {
    def translate(from: TimeInfo): TimeInfoContract = {
      TimeInfoContract(
        submittedAt = from.submittedAt,
        startedAt   = from.startedAt,
        completedAt = from.completedAt
      )
    }
  }

  val toTaskTimeInfoContract = new Translator[TaskTimeInfo, TaskTimeInfoContract] {
    def translate(from: TaskTimeInfo): TaskTimeInfoContract = {
      TaskTimeInfoContract(
        taskName = from.taskName,
        timeInfo = toTimeInfoContract.translate(from.timeInfo)
      )
    }
  }

  implicit val toJobEntityContract = new Translator[JobEntity, JobEntityContract] {
    def translate(from: JobEntity): JobEntityContract = {
      JobEntityContract(
        id              = from.id,
        owner           = from.owner,
        jobType         = from.jobType,
        status          = from.status,
        inputPath       = from.inputPath,
        outputPath      = from.outputPath,
        timeInfo        = toTimeInfoContract.translate(from.timeInfo),
        tasksTimeInfo   = from.tasksTimeInfo.map(toTaskTimeInfoContract.translate),
        tasksQueuedTime = from.tasksQueuedTime
      )
    }
  }

}

class JobHttpEndpoint(jobCommandService: ActorRef, jobQueryService: ActorRef, val config: BaseConfig) extends HttpEndpoint
    with CortexJson4sSupport
    with CRUDRoutes {

  import JobHttpEndpoint._

  val routes: Route =
    pathPrefix("jobs") {
      create(jobCommandService) ~
        remove(jobCommandService) ~
        list(jobQueryService)
    }

}
