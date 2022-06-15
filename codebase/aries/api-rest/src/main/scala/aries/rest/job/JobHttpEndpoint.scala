package aries.rest.job

import akka.actor.ActorRef
import akka.http.scaladsl.server.Route
import aries.common.json4s.AriesJson4sSupport
import aries.common.rest.BaseConfig
import aries.common.rest.routes.{ CRUDRoutes, HttpEndpoint, Translator }
import aries.domain.rest.datacontracts.job.JobDataContract
import aries.domain.rest.datacontracts.job.JobDataContract.CortexErrorDetailsContract
import aries.domain.service.job._

object JobHttpEndpoint {

  def apply(jobCommandService: ActorRef, jobQueryService: ActorRef, config: BaseConfig): JobHttpEndpoint = {
    new JobHttpEndpoint(jobCommandService: ActorRef, jobQueryService: ActorRef, config: BaseConfig)
  }

  implicit val toCreateJobData = new Translator[JobDataContract.CreateRequest, CreateJobData] {
    def translate(from: JobDataContract.CreateRequest): CreateJobData = {
      CreateJobData(
        id           = from.id,
        submitted_at = from.submittedAt,
        owner        = from.owner,
        job_type     = from.jobType,
        status       = from.status,
        input_path   = from.inputPath
      )
    }
  }

  val toTimeInfo = new Translator[JobDataContract.TimeInfo, TimeInfo] {
    def translate(from: JobDataContract.TimeInfo): TimeInfo = {
      TimeInfo(
        submitted_at = from.submittedAt,
        started_at   = from.startedAt,
        completed_at = from.completedAt
      )
    }
  }

  val toTaskTimeInfo = new Translator[JobDataContract.TaskTimeInfo, TaskTimeInfo] {
    def translate(from: JobDataContract.TaskTimeInfo): TaskTimeInfo = {
      TaskTimeInfo(
        task_name = from.taskName,
        time_info = toTimeInfo.translate(from.timeInfo)
      )
    }
  }

  val toUpdateTimeInfo = new Translator[JobDataContract.UpdateTimeInfo, UpdateTimeInfo] {
    def translate(from: JobDataContract.UpdateTimeInfo): UpdateTimeInfo = {
      UpdateTimeInfo(
        submitted_at = from.submittedAt,
        started_at   = from.startedAt,
        completed_at = from.completedAt
      )
    }
  }

  val toCortexErrorDetails = new Translator[CortexErrorDetailsContract, CortexErrorDetails] {
    override def translate(from: CortexErrorDetailsContract): CortexErrorDetails = {
      CortexErrorDetails(
        error_code     = from.errorCode,
        error_messages = from.errorMessages,
        error_details  = from.errorDetails
      )
    }
  }

  val toCortexErrorDetailsContract = new Translator[CortexErrorDetails, CortexErrorDetailsContract] {
    override def translate(from: CortexErrorDetails): CortexErrorDetailsContract = {
      CortexErrorDetailsContract(
        errorCode     = from.error_code,
        errorMessages = from.error_messages,
        errorDetails  = from.error_details
      )
    }
  }

  implicit val toUpdateJobData = new Translator[JobDataContract.UpdateRequest, UpdateJobData] {
    def translate(from: JobDataContract.UpdateRequest): UpdateJobData = {
      UpdateJobData(
        status               = from.status,
        time_info            = from.timeInfo.map(toUpdateTimeInfo.translate),
        tasks_queued_time    = from.tasksQueuedTime,
        tasks_time_info      = from.tasksTimeInfo.map(_.map(toTaskTimeInfo.translate)),
        output_path          = from.outputPath,
        cortex_error_details = from.cortexErrorDetails.map(toCortexErrorDetails.translate)
      )
    }
  }

  val toTimeInfoContract = new Translator[TimeInfo, JobDataContract.TimeInfo] {
    def translate(from: TimeInfo): JobDataContract.TimeInfo = {
      JobDataContract.TimeInfo(
        submittedAt = from.submitted_at,
        startedAt   = from.started_at,
        completedAt = from.completed_at
      )
    }
  }

  val toTaskTimeInfoContract = new Translator[TaskTimeInfo, JobDataContract.TaskTimeInfo] {
    def translate(from: TaskTimeInfo): JobDataContract.TaskTimeInfo = {
      JobDataContract.TaskTimeInfo(
        taskName = from.task_name,
        timeInfo = toTimeInfoContract.translate(from.time_info)
      )
    }
  }

  implicit val toJobDataContract = new Translator[JobEntity, JobDataContract.Response] {
    def translate(from: JobEntity): JobDataContract.Response = {
      JobDataContract.Response(
        id                 = from.id,
        owner              = from.owner,
        jobType            = from.job_type,
        status             = from.status,
        inputPath          = from.input_path,
        timeInfo           = toTimeInfoContract.translate(from.time_info),
        tasksTimeInfo      = from.tasks_time_info.map(toTaskTimeInfoContract.translate),
        tasksQueuedTime    = from.tasks_queued_time,
        outputPath         = from.output_path,
        cortexErrorDetails = from.cortex_error_details.map(toCortexErrorDetailsContract.translate)
      )
    }
  }

}

class JobHttpEndpoint(jobCommandService: ActorRef, jobQueryService: ActorRef, val config: BaseConfig) extends HttpEndpoint
    with AriesJson4sSupport
    with CRUDRoutes {

  import JobHttpEndpoint._

  val routes: Route =
    pathPrefix("jobs") {
      create[JobDataContract.CreateRequest, CreateJobData, JobEntity, JobDataContract.Response](jobCommandService) ~
        update[JobDataContract.UpdateRequest, UpdateJobData, JobEntity, JobDataContract.Response](jobCommandService) ~
        remove(jobCommandService)
    }

}
