package cortex.service.job

import java.util.Date

import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.http.scaladsl.model.{ HttpEntity, HttpResponse, MediaTypes }
import akka.pattern.gracefulStop
import akka.testkit.TestActorRef
import cortex.api.job.message._
import cortex.common.json4s.{ CortexJson4sSupport, Json }
import cortex.domain.service.job._
import cortex.domain.service.{ CreateEntity, DeleteEntity }
import cortex.testkit.service.{ RabbitMqItSupport, ServiceBaseSpec }
import orion.ipc.rabbitmq.MlJobTopology._

import scala.concurrent.Future

class JobCommandServiceItSpec extends ServiceBaseSpec with RabbitMqItSupport {

  // Fixtures
  val jobId = mockRandomUUID
  val submittedAt = mockCurrentDate
  val ownerId = mockRandomUUID
  val jobType = "TRAIN"
  val jobStatus = JobStatus.Submitted
  val inputPath = "some/input/path"
  val startedAt = new Date().withoutMillis()
  val submitJobData = SubmitJobData(None, ownerId, jobType, inputPath)
  val createJobData = CreateJobData(
    jobId,
    ownerId,
    jobType,
    jobStatus,
    inputPath,
    submittedAt
  )
  val jobEntity = JobEntity(
    jobId,
    ownerId,
    jobType,
    jobStatus,
    inputPath,
    TimeInfo(submittedAt, None, None),
    Seq.empty,
    None
  )

  val mockAriesBaseUrl = "http://api.aries.com/v1"
  val mockAriesCommandCredentials = BasicHttpCredentials("mock-command-username", "mock-command-password")

  trait Scope extends ServiceScope with CortexJson4sSupport {
    val service = TestActorRef(new JobCommandService() with DateSupportTesting with UUIDSupportTesting with HttpClientSupportTesting {
      override val ariesBaseUrl = mockAriesBaseUrl
      override val ariesCommandCredentials = mockAriesCommandCredentials
    })
  }

  "When receiving a CreateEntity msg, the service" should {
    "respond with the created Job if no errors" in new Scope {
      // Mock http call
      val responseEntity = HttpEntity(MediaTypes.`application/json`, Json.toJson(jobEntity))
      mockHttpClient
        .postExpects(s"$mockAriesBaseUrl/jobs", createJobData, Some(mockAriesCommandCredentials))
        .returning(Future.successful(HttpResponse(entity = responseEntity)))

      // Send msg
      service ! CreateEntity(submitJobData)

      // Verify Service response
      expectMsg(jobEntity)

      // Verify Msg has been published in RabbitMq
      val expectedMsg = JobMessage(JobMessageMeta(jobId, Some(jobType)), SubmitJob(inputPath))
      getMessage[JobMessage](NewJobQueue).futureValue shouldBe expectedMsg

      // Stop actor when done
      gracefulStop(service, timeout.duration).futureValue shouldBe true
    }
  }

  "When receiving a DeleteEntity msg, the service" should {
    "respond with the deleted User if it exists" in new Scope {
      val id = jobId

      // Mock http call
      val responseEntity = HttpEntity(MediaTypes.`application/json`, Json.toJson(jobEntity))
      mockHttpClient
        .deleteExpects(s"$mockAriesBaseUrl/jobs/$id", Some(mockAriesCommandCredentials))
        .returning(Future.successful(HttpResponse(entity = responseEntity)))

      // Send msg
      service ! DeleteEntity(id)

      // Verify Service response
      expectMsgPF() {
        case Some(jobEntity: JobEntity) =>
          jobEntity.id shouldBe id
      }

      // Verify Msg has been published in RabbitMq
      val expectedMsg = JobMessage(JobMessageMeta(jobId), CancelJob)
      getMessage[JobMessage](CancelJobQueue).futureValue shouldBe expectedMsg

      // Stop actor when done
      gracefulStop(service, timeout.duration).futureValue shouldBe true
    }
  }

  "When submitting a JobStarted msg to RabbitMq, the service" should {
    "send an Update job Http request and log an info message if no errors" in new Scope {
      // TODO: add test
    }
  }

  "When submitting a Heartbeat msg to RabbitMq, the service" should {
    "send a create Heartbeat Http request and log an info message if no errors" in new Scope {
      // TODO: add test
    }
  }

  "When submitting a JobResultSuccess msg to RabbitMq, the service" should {
    "send an Update job Http request and log an info message if no errors" in new Scope {
      // TODO: add test
    }
  }

}
