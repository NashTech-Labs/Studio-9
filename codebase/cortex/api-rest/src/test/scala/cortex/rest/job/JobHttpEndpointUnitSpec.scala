package cortex.rest.job

import java.util.{ Date, UUID }

import akka.http.scaladsl.model.StatusCodes
import akka.testkit.TestProbe
import cortex.api.job.message.TimeInfo
import cortex.common.json4s.CortexJson4sSupport
import cortex.domain.rest.job.{ JobEntityContract, SubmitJobDataContract, TimeInfoContract }
import cortex.domain.service.job.{ JobEntity, JobStatus, SubmitJobData }
import cortex.domain.service.{ CreateEntity, DeleteEntity, ListEntities }
import cortex.testkit.BaseSpec
import cortex.testkit.rest.HttpEndpointBaseSpec

class JobHttpEndpointUnitSpec extends HttpEndpointBaseSpec {

  // Fixtures
  val jobId = UUID.randomUUID()
  val ownerId = UUID.randomUUID()
  val nowDate = new Date().withoutMillis()
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
  val jobEntityContract = JobEntityContract(
    jobId,
    ownerId,
    jobType,
    jobStatus,
    inputPath,
    TimeInfoContract(nowDate, None, None),
    Seq.empty,
    None
  )

  val nonExistentJobId = UUID.randomUUID()

  trait Scope extends RouteScope with CortexJson4sSupport {
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
      val result = Post("/jobs", SubmitJobDataContract(Some(jobId), ownerId, jobType, inputPath)).run

      jobCommandService.expectMsg(CreateEntity(SubmitJobData(Some(jobId), ownerId, jobType, inputPath)))
      jobCommandService.reply(jobEntity)

      result.check {
        response.status shouldBe StatusCodes.Created
        responseAs[JobEntityContract] shouldBe jobEntityContract
      }
    }
    "create a new Job, assign a new Id to it if none is provided and return a 201 Response if the request is valid" in new Scope {
      val result = Post("/jobs", SubmitJobDataContract(None, ownerId, jobType, inputPath)).run

      jobCommandService.expectMsg(CreateEntity(SubmitJobData(None, ownerId, jobType, inputPath)))
      jobCommandService.reply(jobEntity)

      result.check {
        response.status shouldBe StatusCodes.Created
        responseAs[JobEntityContract].id shouldBe jobEntity.id
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

  "When sending a DELETE request, the Job API" should {
    "delete the requested Job and return a 204 Response if the Job exists" in new Scope {
      val result = Delete(s"/jobs/$jobId").run

      jobCommandService.expectMsg(DeleteEntity(jobId))
      jobCommandService.reply(Some(jobEntity))

      result.check {
        response.status shouldBe StatusCodes.OK
        responseAs[JobEntityContract] shouldBe jobEntityContract
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

  "When sending a GET request, the Job API" should {
    "return a list of all Jobs" in new Scope {
      val secondJobId = UUID.randomUUID()
      val allJobEntities = Seq(jobEntity, jobEntity.copy(id = secondJobId))
      val allJobContracts = Seq(jobEntityContract, jobEntityContract.copy(id = secondJobId))

      val result = Get("/jobs").run

      jobQueryService.expectMsg(ListEntities)
      jobQueryService.reply(allJobEntities)

      result.check {
        response.status shouldBe StatusCodes.OK
        responseAs[Seq[JobEntityContract]] should contain theSameElementsAs allJobContracts
      }
    }
  }

}

class JobHttpEndpointTranslatorsSpec extends BaseSpec {
  import JobHttpEndpoint._

  val jobDataContract = SubmitJobDataContract(
    id        = Some(UUID.randomUUID()),
    owner     = UUID.randomUUID(),
    jobType   = "TRAIN",
    inputPath = "some/input/path"
  )

  val jobEntity = JobEntity(
    id              = UUID.randomUUID(),
    owner           = UUID.randomUUID(),
    jobType         = "TRAIN",
    status          = JobStatus.Running,
    inputPath       = "some/input/path",
    timeInfo        = TimeInfo(new Date(), Some(new Date()), Some(new Date())),
    tasksTimeInfo   = Seq.empty,
    tasksQueuedTime = None,
    outputPath      = Some("some/output/path")
  )

  "JobDataContract To JobData translator" should {
    "translate JobDataContract to JobData" in {
      val result = toJobData.translate(jobDataContract)
      result.id shouldBe jobDataContract.id
      result.owner shouldBe jobDataContract.owner
      result.inputPath shouldBe jobDataContract.inputPath
    }
  }

  "JobEntity To JobEntityContract translator" should {
    "translate JobEntity to JobEntityContract" in {
      val result = toJobEntityContract.translate(jobEntity)
      result.id shouldBe jobEntity.id
      result.owner shouldBe jobEntity.owner
      result.status shouldBe jobEntity.status
      result.inputPath shouldBe jobDataContract.inputPath
    }
  }
}
