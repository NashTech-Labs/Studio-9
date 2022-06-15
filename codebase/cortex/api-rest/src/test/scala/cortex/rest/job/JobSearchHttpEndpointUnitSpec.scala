package cortex.rest.job

import java.util.{ Date, UUID }

import akka.http.scaladsl.model._
import akka.testkit.TestProbe
import cortex.api.job.message.TimeInfo
import cortex.common.json4s.CortexJson4sSupport
import cortex.domain.rest.job.{ JobEntityContract, JobSearchCriteriaContract }
import cortex.domain.service.job._
import cortex.testkit.BaseSpec
import cortex.testkit.rest.HttpEndpointBaseSpec

class JobSearchHttpEndpointUnitSpec extends HttpEndpointBaseSpec {

  // Fixtures
  val jobId = UUID.randomUUID()
  val ownerId = UUID.randomUUID()
  val nowDate = new Date()
  val jobType = "TRAIN"
  val jobStatus = JobStatus.Running
  val inputPath = "some/input/path"
  val jobEntity = JobEntity(
    jobId,
    ownerId,
    jobType,
    jobStatus,
    inputPath,
    TimeInfo(nowDate, None, None),
    Seq.empty,
    None
  )

  val jobEntities = Seq(jobEntity)

  trait Scope extends RouteScope with CortexJson4sSupport {
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
      val requestEntity = JobSearchCriteria(owner = Some(ownerId), status = Some(jobStatus))

      val result = Post(jobSearchEndpoint, requestEntity).run
      jobQueryService.expectMsg(FindJob(JobSearchCriteria(owner = Some(ownerId), status = Some(jobStatus))))
      jobQueryService.reply(jobEntities)

      result.check {
        status shouldBe StatusCodes.OK
        contentType shouldBe ContentTypes.`application/json`
        responseAs[Seq[JobEntityContract]] should have size 1
      }
    }
    "find the best match job by search criteria in a query string" in new Scope {
      val result = Get(jobSearchEndpoint).withQuery("owner" -> ownerId.toString).run
      jobQueryService.expectMsg(FindJob(JobSearchCriteria(owner = Some(ownerId))))
      jobQueryService.reply(jobEntities)

      result.check {
        status shouldBe StatusCodes.OK
        contentType shouldBe ContentTypes.`application/json`
        responseAs[Seq[JobEntityContract]] should have size 1
      }
    }
    "return OK response if there isn't any job found" in new Scope {
      val requestEntity = JobSearchCriteria(owner = Some(ownerId))

      val result = Post(jobSearchEndpoint, requestEntity).run
      jobQueryService.expectMsg(FindJob(JobSearchCriteria(owner = Some(ownerId))))
      jobQueryService.reply(Seq.empty)

      result.check {
        status shouldBe StatusCodes.OK
        responseAs[Seq[JobEntityContract]] shouldBe empty
      }
    }
  }

}

class JobSearchHttpEndpointTranslatorsSpec extends BaseSpec {
  import JobSearchHttpEndpoint._

  val jobSearchCriteriaContract = JobSearchCriteriaContract(
    owner   = Some(UUID.randomUUID()),
    jobType = Some("TRAIN"),
    status  = Some(JobStatus.Running)
  )

  "JobSearchCriteriaContract To JobSearchCriteria translator" should {
    "translate JobSearchCriteriaContract to JobSearchCriteria" in {
      val result = toJobSearchCriteria.translate(jobSearchCriteriaContract)
      result.owner shouldBe jobSearchCriteriaContract.owner
      result.jobType shouldBe jobSearchCriteriaContract.jobType
      result.status shouldBe jobSearchCriteriaContract.status
    }

    "translate JobSearchCriteriaContract to Try of JobSearchCriteria" in {
      val result = toJobSearchCriteria.tryToTranslate(jobSearchCriteriaContract)
      result.isSuccess shouldBe true
    }
  }

}
