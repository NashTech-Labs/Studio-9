package cortex.rest.job

import java.util.{ Date, UUID }

import akka.http.scaladsl.model.{ ContentTypes, StatusCodes }
import akka.testkit.TestProbe
import cortex.common.json4s.CortexJson4sSupport
import cortex.domain.rest.job.{ CortexErrorDetailsContract, JobStatusDataContract }
import cortex.domain.service.job._
import cortex.testkit.rest.HttpEndpointBaseSpec

import scala.concurrent.duration._

class JobStatusHttpEndpointUnitSpec extends HttpEndpointBaseSpec {

  // Fixtures
  val jobId = UUID.randomUUID()
  val nowDate = new Date()
  val currentProgress = Some(0.45D)
  val estimatedTimeRemaining = Some(2 hours)
  val errorCode = "errorCode"
  val errorMessage = "errorMessage"
  val errorDetails = Map("stackTrace" -> "stackTrace")
  val jobStatusData = JobStatusData(
    JobStatus.Running,
    currentProgress,
    estimatedTimeRemaining,
    Some(CortexErrorDetails(errorCode, errorMessage, errorDetails))
  )
  val jobStatusDataContract = JobStatusDataContract(
    JobStatus.Running,
    currentProgress,
    estimatedTimeRemaining,
    Some(CortexErrorDetailsContract(errorCode, errorMessage, errorDetails))
  )

  trait Scope extends RouteScope with CortexJson4sSupport {
    val jobQueryService = TestProbe()
    val httpEndpoint = new JobStatusHttpEndpoint(
      jobQueryService.ref,
      testAppConfig
    )

    val routes = httpEndpoint.routes

  }

  "When sending a GET request to /jobs/{jobId}/status endpoint, it" should {
    "return a 200 Response with the JobStatus for the requested Job if it exists" in new Scope {
      val result = Get(s"/jobs/$jobId/status").run
      jobQueryService.expectMsg(GetJobStatus(jobId))
      jobQueryService.reply(Some(jobStatusData))

      result.check {
        status shouldBe StatusCodes.OK
        contentType shouldBe ContentTypes.`application/json`

        responseAs[JobStatusDataContract] shouldBe jobStatusDataContract
      }
    }
    "return a 404 Response if the JobStatus for the requested Job does not exist" in new Scope {
      val nonExistentJobId = UUID.randomUUID()

      val result = Get(s"/jobs/$nonExistentJobId/status").run
      jobQueryService.expectMsg(GetJobStatus(nonExistentJobId))
      jobQueryService.reply(None)

      result.check {
        response.status shouldBe StatusCodes.NotFound
      }
    }
  }

}