package aries.rest.job

import java.util.UUID

import akka.http.scaladsl.model._
import akka.testkit.TestProbe
import aries.common.json4s.AriesJson4sSupport
import aries.domain.rest.datacontracts.job.JobDataContract
import aries.domain.service.{ ListEntities, RetrieveEntity }

import aries.domain.service.job._
import aries.testkit.BaseSpec
import aries.testkit.rest.HttpEndpointBaseSpec
import aries.testkit.service.DateSupportMocks

import scala.concurrent.duration._

class JobSearchHttpEndpointSpec extends HttpEndpointBaseSpec with DateSupportMocks {

  // Fixtures
  val jobId = UUID.randomUUID()
  val ownerId = UUID.randomUUID()
  val nowDate = mockCurrentDate
  val jobType = "TRAIN"
  val jobStatus = JobStatus.Running
  val inputPath = "some/input/path"
  val timeInfo = TimeInfo(nowDate, Some(nowDate), Some(nowDate))
  val tasksTimeInfo = Seq(TaskTimeInfo("task1", timeInfo))
  val jobDataContractTimeInfo = JobDataContract.TimeInfo(nowDate, Some(nowDate), Some(nowDate))
  val jobDataContractTasksTimeInfo = Seq(JobDataContract.TaskTimeInfo("task1", jobDataContractTimeInfo))
  val tasksQueuedTime = 20 minutes
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

  val jobEntities = Seq(jobEntity)
  val jobDataContracts = Seq(jobDataContractResp)

  trait Scope extends RouteScope with AriesJson4sSupport {
    val jobQueryService = TestProbe()
    val jobServiceRoute = new JobSearchHttpEndpoint(
      jobQueryService.ref,
      testAppConfig
    )

    val routes = jobServiceRoute.routes

  }

  "When sending a GET request to /jobs/search endpoint, it" should {
    val jobSearchEndpoint = "/jobs/search"
    "find the best match job by search criteria" in new Scope {
      val requestEntity = JobDataContract.SearchRequest(owner = Some(ownerId), status = Some(jobStatus))

      val result = Post(jobSearchEndpoint, requestEntity).run
      jobQueryService.expectMsg(FindJob(JobSearchCriteria(owner = Some(ownerId), status = Some(jobStatus))))
      jobQueryService.reply(jobEntities)

      result.check {
        status shouldBe StatusCodes.OK
        contentType shouldBe ContentTypes.`application/json`
        responseAs[Seq[JobDataContract.Response]] shouldBe jobDataContracts
      }
    }
    "find the best match job by search criteria in a query string" in new Scope {
      val result = Get(jobSearchEndpoint).withQuery("owner" -> ownerId.toString).run
      jobQueryService.expectMsg(FindJob(JobSearchCriteria(owner = Some(ownerId))))
      jobQueryService.reply(jobEntities)

      result.check {
        status shouldBe StatusCodes.OK
        contentType shouldBe ContentTypes.`application/json`
        responseAs[Seq[JobDataContract.Response]] shouldBe jobDataContracts
      }
    }
    "return OK response if there isn't any job found" in new Scope {
      val requestEntity = JobDataContract.SearchRequest(owner = Some(ownerId))

      val result = Post(jobSearchEndpoint, requestEntity).run
      jobQueryService.expectMsg(FindJob(JobSearchCriteria(owner = Some(ownerId))))
      jobQueryService.reply(Seq.empty)

      result.check {
        status shouldBe StatusCodes.OK
        responseAs[Seq[JobDataContract.Response]] shouldBe empty
      }
    }
  }

  "When sending a GET request, the Job API" should {
    "return a 200 Response with the requested Job if it exists" in new Scope {
      val result = Get(s"/jobs/$jobId").run

      jobQueryService.expectMsg(RetrieveEntity(jobId))
      jobQueryService.reply(Some(jobEntity))

      result.check {
        response.status shouldBe StatusCodes.OK
        responseAs[JobDataContract.Response] shouldBe jobDataContractResp
      }
    }
    "return a 404 Response if the requested Job does not exist" in new Scope {
      val nonExistentJobId = UUID.randomUUID()
      val result = Get(s"/jobs/$nonExistentJobId").runSeal

      jobQueryService.expectMsg(RetrieveEntity(nonExistentJobId))
      jobQueryService.reply(None)

      result.check {
        response.status shouldBe StatusCodes.NotFound
      }
    }
  }

  "When sending a GET request, the Job API" should {
    "return a list of all Jobs" in new Scope {
      val secondJobId = UUID.randomUUID()
      val allJobEntities = Seq(jobEntity, jobEntity.copy(id = secondJobId))
      val allJobContracts = Seq(jobDataContractResp, jobDataContractResp.copy(id = secondJobId))

      val result = Get("/jobs").run

      jobQueryService.expectMsg(ListEntities)
      jobQueryService.reply(allJobEntities)

      result.check {
        response.status shouldBe StatusCodes.OK
        responseAs[Seq[JobDataContract.Response]].size shouldBe allJobEntities.size
      }
    }
  }

}

class JobSearchHttpEndpointTranslatorsSpec extends BaseSpec {

  import JobSearchHttpEndpoint._

  val jobDataContract = JobDataContract.SearchRequest(
    owner   = Some(UUID.randomUUID()),
    jobType = Some("TRAIN"),
    status  = Some(JobStatus.Running)
  )

  "JobDataContract.SearchRequest to JobSearchCriteria translator" should {
    "translate JobDataContract.SearchRequest to JobSearchCriteria" in {
      val result = toJobSearchCriteria.translate(jobDataContract)
      result.owner shouldBe jobDataContract.owner
      result.job_type shouldBe jobDataContract.jobType
      result.status shouldBe jobDataContract.status
    }

    "translate JobDataContract.SearchRequest to try of JobSearchCriteria" in {
      val result = toJobSearchCriteria.tryToTranslate(jobDataContract)
      result.isSuccess shouldBe true
    }
  }
}
