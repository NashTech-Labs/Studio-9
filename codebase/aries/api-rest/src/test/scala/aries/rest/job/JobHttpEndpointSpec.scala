package aries.rest.job

import java.util.UUID

import akka.http.scaladsl.model.StatusCodes
import akka.testkit.TestProbe
import aries.common.json4s.AriesJson4sSupport
import aries.domain.rest.datacontracts.job.JobDataContract
import aries.domain.rest.datacontracts.job.JobDataContract.CortexErrorDetailsContract
import aries.domain.service._
import aries.domain.service.job._
import aries.testkit.BaseSpec
import aries.testkit.rest.HttpEndpointBaseSpec
import aries.testkit.service.DateSupportMocks

import scala.concurrent.duration._

class JobHttpEndpointSpec extends HttpEndpointBaseSpec with DateSupportMocks {

  // Fixtures
  val jobId = UUID.randomUUID()
  val ownerId = UUID.randomUUID()
  val nowDate = mockCurrentDate
  val jobType = "TRAIN"
  val jobStatus = JobStatus.Running
  val inputPath = "some/input/path"
  val timeInfo = TimeInfo(nowDate, None, None)
  val tasksTimeInfo = Seq(TaskTimeInfo("task1", timeInfo))
  val tasksQueuedTime = 20 minutes
  val createJobData = CreateJobData(jobId, nowDate, ownerId, jobType, jobStatus, inputPath)
  val updateJobData = UpdateJobData(status = Some(JobStatus.Running))
  val jobEntity = JobEntity(
    id                = jobId,
    owner             = ownerId,
    job_type          = jobType,
    status            = jobStatus,
    input_path        = inputPath,
    time_info         = timeInfo,
    tasks_queued_time = Some(tasksQueuedTime),
    tasks_time_info   = tasksTimeInfo
  )
  val jobDataContractTimeInfo = JobDataContract.TimeInfo(nowDate, None, None)
  val jobDataContractTasksTimeInfo = Seq(JobDataContract.TaskTimeInfo("task1", jobDataContractTimeInfo))
  val jobDataContractCR = JobDataContract.CreateRequest(jobId, ownerId, jobType, jobStatus, inputPath, nowDate)
  val jobDataContractUR = JobDataContract.UpdateRequest(status = Some(JobStatus.Running))
  val jobDataContractResp = JobDataContract.Response(
    id              = jobId,
    owner           = ownerId,
    jobType         = jobType,
    status          = jobStatus,
    inputPath       = inputPath,
    timeInfo        = jobDataContractTimeInfo,
    tasksTimeInfo   = jobDataContractTasksTimeInfo,
    tasksQueuedTime = Some(tasksQueuedTime)
  )
  val updatedJobEntity = jobEntity.copy(status = JobStatus.Running)
  val updatedJobDataContractResp = jobDataContractResp.copy(status = JobStatus.Running)

  val nonExistentJobId = UUID.randomUUID()

  trait Scope extends RouteScope with AriesJson4sSupport {
    val jobCommandService = TestProbe()
    val jobQueryService = TestProbe()

    val jobServiceRoute = new JobHttpEndpoint(
      jobCommandService.ref,
      jobQueryService.ref,
      testAppConfig
    )

    val routes = jobServiceRoute.routes

  }

  "When sending a POST request, the Job API" should {
    "create a new Job and return a 201 Response if the request is valid" in new Scope {
      val result = Post("/jobs", jobDataContractCR).run

      jobCommandService.expectMsg(CreateEntity(createJobData))
      jobCommandService.reply(jobEntity)

      result.check {
        response.status shouldBe StatusCodes.Created
        responseAs[JobDataContract.Response] shouldBe jobDataContractResp
      }
    }
    "not create a Job and return a 400 Response if the request is not valid" in new Scope {
      val emptyOwner = "" // Owner must not be empty
      val result = Post("/jobs", Map("owner" -> emptyOwner)).runSeal

      jobCommandService.expectNoMsg()

      result.check {
        response.status shouldBe StatusCodes.BadRequest
      }
    }
  }

  "When sending a PUT request, the Job API" should {
    "update the Job and return a 200 Response with the requested Job if it exists" in new Scope {
      val result = Put(s"/jobs/$jobId", jobDataContractUR).run

      jobCommandService.expectMsg(UpdateEntity(jobId, updateJobData))
      jobCommandService.reply(Some(updatedJobEntity))

      result.check {
        response.status shouldBe StatusCodes.OK
        responseAs[JobDataContract.Response] shouldBe updatedJobDataContractResp
      }
    }
    "not create a Job and return a 400 Response if the request is not valid" in new Scope {
      val emptyOwner = "" // Owner must not be empty
      val result = Post("/jobs", Map("owner" -> emptyOwner)).runSeal

      jobCommandService.expectNoMsg()

      result.check {
        response.status shouldBe StatusCodes.BadRequest
      }
    }
    "update the Job with cortex error and return a 200 Response with the requested Job if it exists" in new Scope {
      val jobDataContractUpdateRequest = JobDataContract.UpdateRequest(
        status             = Some(JobStatus.Failed),
        cortexErrorDetails = Some(CortexErrorDetailsContract("ec-101", "message", Map("st" -> "eD")))
      )
      val updateJobData = UpdateJobData(
        status               = Some(JobStatus.Failed),
        cortex_error_details = Some(CortexErrorDetails("ec-101", "message", Map("st" -> "eD")))
      )
      val updatedJobEntityWithCortexError: JobEntity = jobEntity.copy(
        status               = JobStatus.Failed,
        cortex_error_details = Some(CortexErrorDetails("ec-101", "message", Map("st" -> "eD")))
      )
      val updatedJobDataContractResponse: JobDataContract.Response = jobDataContractResp.copy(
        status             = JobStatus.Failed,
        cortexErrorDetails = Some(CortexErrorDetailsContract("ec-101", "message", Map("st" -> "eD")))
      )

      val result: RouteTestResult = Put(s"/jobs/$jobId", jobDataContractUpdateRequest).run()

      jobCommandService.expectMsg(UpdateEntity(jobId, updateJobData))
      jobCommandService.reply(Some(updatedJobEntityWithCortexError))

      result.check {
        response.status shouldBe StatusCodes.OK
        responseAs[JobDataContract.Response] shouldBe updatedJobDataContractResponse
      }
    }
  }

  "When sending a DELETE request, the Job API" should {
    "delete the requested Job and return a 200 Response with the requested job if the Job exists" in new Scope {
      val result = Delete(s"/jobs/$jobId").run

      jobCommandService.expectMsg(DeleteEntity(jobId))
      jobCommandService.reply(Some(jobEntity))

      result.check {
        response.status shouldBe StatusCodes.OK
        responseAs[JobDataContract.Response] shouldBe jobDataContractResp
      }
    }
    "return a 404 Response if the requested Job does not exist" in new Scope {
      val result = Delete(s"/jobs/$nonExistentJobId").runSeal

      jobCommandService.expectMsg(DeleteEntity(nonExistentJobId))
      jobCommandService.reply(None)

      result.check {
        response.status shouldBe StatusCodes.NotFound
      }
    }
  }

}

class JobHttpEndpointTranslatorsSpec extends BaseSpec with DateSupportMocks {
  import JobHttpEndpoint._

  val jobDataContractCR = JobDataContract.CreateRequest(
    id          = UUID.randomUUID(),
    submittedAt = mockCurrentDate,
    owner       = UUID.randomUUID(),
    jobType     = "TRAIN",
    status      = JobStatus.Submitted,
    inputPath   = "some/input/path",
    startedAt   = Some(mockCurrentDate),
    completedAt = Some(mockCurrentDate),
    outputPath  = Some("some/output/path")
  )

  val jobDataContractUpdateTimeInfo = JobDataContract.UpdateTimeInfo(
    None,
    Some(mockCurrentDate),
    Some(mockCurrentDate)
  )

  val jobDataContractTimeInfo = JobDataContract.TimeInfo(
    mockCurrentDate,
    Some(mockCurrentDate),
    Some(mockCurrentDate)
  )

  val jobDataContractTaskTimeInfo = JobDataContract.TaskTimeInfo("task1", jobDataContractTimeInfo)

  val jobDataContractUR = JobDataContract.UpdateRequest(
    status          = Some(JobStatus.Submitted),
    timeInfo        = Some(jobDataContractUpdateTimeInfo),
    tasksQueuedTime = Some(20 minutes),
    tasksTimeInfo   = Some(Seq(jobDataContractTaskTimeInfo)),
    outputPath      = Some("some/output/path")
  )

  val timeInfo = TimeInfo(
    mockCurrentDate,
    Some(mockCurrentDate),
    Some(mockCurrentDate)
  )

  val taskTimeInfo = TaskTimeInfo("task1", timeInfo)

  val jobEntity = JobEntity(
    id                = UUID.randomUUID(),
    owner             = UUID.randomUUID(),
    job_type          = "TRAIN",
    status            = JobStatus.Submitted,
    input_path        = "some/input/path",
    time_info         = TimeInfo(mockCurrentDate, Some(mockCurrentDate), Some(mockCurrentDate)),
    tasks_queued_time = Some(20 minutes),
    tasks_time_info   = Seq(TaskTimeInfo("task1", TimeInfo(mockCurrentDate, Some(mockCurrentDate), Some(mockCurrentDate)))),
    output_path       = Some("some/output/path")
  )

  "JobDataContract.CreateRequest To CreateJobData translator" should {
    "translate JobDataContract.CreateRequest to CreateJobData" in {
      val result = toCreateJobData.translate(jobDataContractCR)
      result.id shouldBe jobDataContractCR.id
      result.submitted_at shouldBe jobDataContractCR.submittedAt
      result.owner shouldBe jobDataContractCR.owner
      result.job_type shouldBe jobDataContractCR.jobType
      result.status shouldBe jobDataContractCR.status
      result.input_path shouldBe jobDataContractCR.inputPath
    }
  }

  "JobDataContract.TimeInfo to TimeInfo translator" should {
    "translate JobDataContract.TimeInfo to TimeInfo" in {
      val result = toTimeInfo.translate(jobDataContractTimeInfo)
      result.submitted_at shouldBe jobDataContractTimeInfo.submittedAt
      result.started_at shouldBe jobDataContractTimeInfo.startedAt
      result.completed_at shouldBe jobDataContractTimeInfo.completedAt
    }
  }

  "JobDataContract.TaskTimeInfo to TaskTimeInfo translator" should {
    "translate JobDataContract.TaskTimeInfo to TimeInfo" in {
      val result = toTaskTimeInfo.translate(jobDataContractTaskTimeInfo)
      result.task_name shouldBe jobDataContractTaskTimeInfo.taskName
      toTimeInfoContract.translate(result.time_info) shouldBe jobDataContractTaskTimeInfo.timeInfo
    }
  }

  "TimeInfo to JobDataContract.TimeInfo translator" should {
    "translate TimeInfo to JobDataContract.TimeInfo" in {
      val result = toTimeInfoContract.translate(timeInfo)
      result.submittedAt shouldBe timeInfo.submitted_at
      result.startedAt shouldBe timeInfo.started_at
      result.completedAt shouldBe timeInfo.completed_at
    }
  }

  "TaskTimeInfo to JobDataContract.TaskTimeInfo translator" should {
    "translate TaskTimeInfo to JobDataContract.TasskTimeInfo" in {
      val result = toTaskTimeInfoContract.translate(taskTimeInfo)
      result.taskName shouldBe taskTimeInfo.task_name
      toTimeInfo.translate(result.timeInfo) shouldBe taskTimeInfo.time_info
    }
  }

  "JobDataContract.UpdateRequest To UpdateJobData translator" should {
    "translate JobDataContract.UpdateRequest to UpdateJobData" in {
      val result = toUpdateJobData.translate(jobDataContractUR)
      result.status shouldBe jobDataContractUR.status
      jobDataContractUR.timeInfo.map(toUpdateTimeInfo.translate) shouldBe result.time_info
      result.tasks_queued_time shouldBe jobDataContractUR.tasksQueuedTime
      result.tasks_time_info.map(_.map(toTaskTimeInfoContract.translate)) shouldBe jobDataContractUR.tasksTimeInfo
      result.tasks_queued_time shouldBe jobDataContractUR.tasksQueuedTime
      result.output_path shouldBe jobDataContractUR.outputPath
    }
  }

  "JobEntity To JobDataContract.Response translator" should {
    "translate JobEntity to JobDataContract.Response" in {
      val result = toJobDataContract.translate(jobEntity)
      result.id shouldBe jobEntity.id
      result.owner shouldBe jobEntity.owner
      result.jobType shouldBe jobEntity.job_type
      result.status shouldBe jobEntity.status
      result.inputPath shouldBe jobEntity.input_path
      toTimeInfo.translate(result.timeInfo) shouldBe jobEntity.time_info
      result.tasksTimeInfo.map(toTaskTimeInfo.translate) shouldBe jobEntity.tasks_time_info
      result.tasksQueuedTime shouldBe jobEntity.tasks_queued_time
      result.outputPath shouldBe jobEntity.output_path
    }
  }
}

