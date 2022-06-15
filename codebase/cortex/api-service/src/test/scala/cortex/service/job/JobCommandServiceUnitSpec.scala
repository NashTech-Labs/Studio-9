package cortex.service.job

import java.util.{ Date, UUID }

import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.pattern.gracefulStop
import akka.testkit.TestActorRef
import com.spingo.op_rabbit.Message
import cortex.api.job.message._
import cortex.common.json4s.{ CortexJson4sSupport, Json }
import cortex.domain.service.job._
import cortex.domain.service.{ CreateEntity, DeleteEntity }
import cortex.testkit.service.ServiceBaseSpec
import org.scalatest.concurrent.ScalaFutures
import orion.ipc.rabbitmq.MlJobTopology
import orion.ipc.rabbitmq.MlJobTopology._

import scala.concurrent.Future
import scala.concurrent.duration._

class JobCommandServiceUnitSpec extends ServiceBaseSpec with ScalaFutures {

  // Fixtures
  val jobId = mockRandomUUID
  val submittedAt = mockCurrentDate
  val ownerId = mockRandomUUID
  val jobType = "TRAIN"
  val jobStatus = JobStatus.Submitted
  val inputPath = "some/input/path"
  val outputPath = "some/output/path"
  val startedAt = new Date().withoutMillis()
  val completedAt = new Date().withoutMillis()
  val timeInfo = TimeInfo(submittedAt, None, None)
  val tasksTimeInfo = Seq(TaskTimeInfo("task1", timeInfo), TaskTimeInfo("task2", timeInfo))
  val tasksQueuedTime = 20 minutes
  val submitJobData = SubmitJobData(None, ownerId, jobType, inputPath)
  val createJobData = CreateJobData(jobId, ownerId, jobType, jobStatus, inputPath, submittedAt)
  val jobEntity = JobEntity(jobId, ownerId, jobType, jobStatus, inputPath, timeInfo, tasksTimeInfo, Some(tasksQueuedTime))

  val mockAriesBaseUrl = "http://api.aries.com/v1"
  val mockAriesCommandCredentials = BasicHttpCredentials("mock-command-username", "mock-command-password")
  val mockAriesRequestRetryCount = 0

  trait Scope extends ServiceScope with CortexJson4sSupport {
    mockRabbitMqClient.subscribeExpects[JobMessage](MlJobTopology.JobStatusQueue)

    val service = TestActorRef(new JobCommandService with DateSupportTesting with UUIDSupportTesting with HttpClientSupportTesting with RabbitMqRpcClientSupportTesting {
      override val ariesBaseUrl = mockAriesBaseUrl
      override val ariesCommandCredentials = mockAriesCommandCredentials
      override val ariesRequestRetryCount = mockAriesRequestRetryCount
    })
  }

  "When receiving a CreateEntity msg, the service" should {
    "respond with the created Job if no errors" in new Scope {
      // Mock Http call
      val responseEntity = HttpEntity(MediaTypes.`application/json`, Json.toJson(jobEntity))
      mockHttpClient.postExpects(s"$mockAriesBaseUrl/jobs", createJobData, Some(mockAriesCommandCredentials)).returning(Future.successful(HttpResponse(entity = responseEntity)))

      // Mock RabbitMq call
      val msg = JobMessage(JobMessageMeta(jobId, Some(jobType)), SubmitJob(inputPath))
      mockRabbitMqClient
        .sendMessageToExchangeWithConfirmationExpects(msg, GatewayExchange, NewJobRoutingKeyTemplate.format(jobId))
        .returning(Future.successful(Message.Ack(1L)))

      // Send msg
      service ! CreateEntity(submitJobData)

      // Verify service response
      expectMsg(jobEntity)

      // Stop actor when done
      gracefulStop(service, timeout.duration).futureValue shouldBe true
    }
    "respond with the created Job if a JobId is provided and no errors" in new Scope {
      val providedJobId = UUID.randomUUID()
      val createJobDataWithProvidedId = createJobData.copy(id = providedJobId)
      val jobEntityWithProvidedId = jobEntity.copy(id = providedJobId)

      // Mock Http call
      val responseEntity = HttpEntity(MediaTypes.`application/json`, Json.toJson(jobEntityWithProvidedId))
      mockHttpClient.postExpects(s"$mockAriesBaseUrl/jobs", createJobDataWithProvidedId, Some(mockAriesCommandCredentials)).returning(Future.successful(HttpResponse(entity = responseEntity)))

      // Mock RabbitMq call
      val msg = JobMessage(JobMessageMeta(providedJobId, Some(jobType)), SubmitJob(inputPath))
      mockRabbitMqClient
        .sendMessageToExchangeWithConfirmationExpects(msg, GatewayExchange, NewJobRoutingKeyTemplate.format(providedJobId))
        .returning(Future.successful(Message.Ack(1L)))

      // Send msg
      service ! CreateEntity(submitJobData.copy(id = Some(providedJobId)))

      // Verify service response
      expectMsg(jobEntityWithProvidedId)

      // Stop actor when done
      gracefulStop(service, timeout.duration).futureValue shouldBe true
    }
    "respond with a Failure msg if the request for creating the job fails" in new Scope {
      val jobData = SubmitJobData(None, ownerId, jobType, inputPath)
      val httpException = new RuntimeException("BOOM!")

      // Mock Http call
      val responseEntity = HttpEntity(MediaTypes.`application/json`, Json.toJson(jobEntity))
      mockHttpClient.postExpects(s"$mockAriesBaseUrl/jobs", createJobData, Some(mockAriesCommandCredentials)).returning(Future.failed(httpException))

      // Verify RabbitMq is not called
      val msg = JobMessage(JobMessageMeta(jobId, Some(jobType)), SubmitJob(inputPath))
      mockRabbitMqClient
        .sendMessageToExchangeWithConfirmationExpects(msg, GatewayExchange, NewJobRoutingKeyTemplate.format(jobId))
        .never()

      // Send msg
      service ! CreateEntity(SubmitJobData(None, ownerId, jobType, inputPath))

      // Verify service response
      expectMsgFailure(httpException)

      // Stop actor when done
      gracefulStop(service, timeout.duration).futureValue shouldBe true
    }
    "respond with a Failure msg if submitting the job for execution the job fails" in new Scope {
      // Mock Http call
      val responseEntity = HttpEntity(MediaTypes.`application/json`, Json.toJson(jobEntity))
      mockHttpClient.postExpects(s"$mockAriesBaseUrl/jobs", createJobData, Some(mockAriesCommandCredentials)).returning(Future.successful(HttpResponse(entity = responseEntity)))

      // Mock RabbitMq call
      val msg = JobMessage(JobMessageMeta(jobId, Some(jobType)), SubmitJob(inputPath))
      val rabbitMqException = new RuntimeException("BOOM!")
      mockRabbitMqClient
        .sendMessageToExchangeWithConfirmationExpects(msg, GatewayExchange, NewJobRoutingKeyTemplate.format(jobId))
        .returning(Future.failed(rabbitMqException))

      // Send msg
      service ! CreateEntity(SubmitJobData(None, ownerId, jobType, inputPath))

      // Verify service response
      expectMsgFailure(rabbitMqException)

      // Stop actor when done
      gracefulStop(service, timeout.duration).futureValue shouldBe true
    }
  }

  "When receiving a DeleteEntity msg, the service" should {
    "respond with the deleted User if it exists" in new Scope {
      val id = jobId

      // Mock Http call
      val responseEntity = HttpEntity(MediaTypes.`application/json`, Json.toJson(jobEntity))
      mockHttpClient.deleteExpects(s"$mockAriesBaseUrl/jobs/$id", Some(mockAriesCommandCredentials)).returning(Future.successful(HttpResponse(entity = responseEntity)))

      // Mock RabbitMq call
      val msg = JobMessage(JobMessageMeta(jobId), CancelJob)
      mockRabbitMqClient
        .sendMessageToExchangeWithConfirmationExpects(msg, GatewayExchange, CancelJobRoutingKeyTemplate.format(jobId))
        .returning(Future.successful(Message.Ack(1L)))

      // Send msg
      service ! DeleteEntity(id)

      // Verify service response
      expectMsgPF() {
        case Some(jobEntity: JobEntity) =>
          jobEntity.id shouldBe id
      }

      // Stop actor when done
      gracefulStop(service, timeout.duration).futureValue shouldBe true
    }
    "respond with None if a Job for the given Id does not exist" in new Scope {
      val nonExistentId = UUID.randomUUID()

      // Mock http call
      mockHttpClient.deleteExpects(s"$mockAriesBaseUrl/jobs/$nonExistentId", Some(mockAriesCommandCredentials)).returning(Future.successful(HttpResponse(StatusCodes.NotFound)))

      // Verify RabbitMq is not called
      val msg = JobMessage(JobMessageMeta(jobId), CancelJob)
      mockRabbitMqClient
        .sendMessageToExchangeWithConfirmationExpects(msg, GatewayExchange, CancelJobRoutingKeyTemplate.format(jobId))
        .never()

      // Send msg
      service ! DeleteEntity(nonExistentId)

      // Verify service response
      expectMsg(None)

      // Stop actor when done
      gracefulStop(service, timeout.duration).futureValue shouldBe true
    }
    "respond with a Failure msg if the call for deleting the Job fails" in new Scope {
      val id = jobId
      val httpException = new RuntimeException("BOOM!")

      // Mock http call
      mockHttpClient.deleteExpects(s"$mockAriesBaseUrl/jobs/$id", Some(mockAriesCommandCredentials)).returning(Future.failed(httpException))

      // Verify RabbitMq is not called
      val msg = JobMessage(JobMessageMeta(id), CancelJob)
      mockRabbitMqClient
        .sendMessageToExchangeWithConfirmationExpects(msg, GatewayExchange, CancelJobRoutingKeyTemplate.format(id))
        .never()

      // Send msg
      service ! DeleteEntity(jobId)

      // Verify service response
      expectMsgFailure(httpException)

      // Stop actor when done
      gracefulStop(service, timeout.duration).futureValue shouldBe true
    }
    "respond with a Failure msg if submitting the job for cancellation fails" in new Scope {
      val id = jobId

      // Mock http call
      val responseEntity = HttpEntity(MediaTypes.`application/json`, Json.toJson(jobEntity))
      mockHttpClient.deleteExpects(s"$mockAriesBaseUrl/jobs/$id", Some(mockAriesCommandCredentials)).returning(Future.successful(HttpResponse(entity = responseEntity)))

      // Mock RabbitMq call
      val msg = JobMessage(JobMessageMeta(jobId), CancelJob)
      val rabbitMqException = new RuntimeException("BOOM!")
      mockRabbitMqClient
        .sendMessageToExchangeWithConfirmationExpects(msg, GatewayExchange, CancelJobRoutingKeyTemplate.format(id))
        .returning(Future.failed(rabbitMqException))

      // Send msg
      service ! DeleteEntity(id)

      // Verify service response
      expectMsgFailure(rabbitMqException)

      // Stop actor when done
      gracefulStop(service, timeout.duration).futureValue shouldBe true
    }
  }

  "When receiving a JobStarted msg, the service" should {
    "send an Update job Http request and log an info message if no errors" in new Scope {
      // Mock http call
      val expectedUpdateData = UpdateJobData(
        status   = Some(JobStatus.Running),
        timeInfo = Some(UpdateTimeInfo(startedAt = Some(startedAt)))
      )
      val expectedTimeInfo = timeInfo.copy(startedAt = Some(startedAt))
      val expectedEntity = jobEntity.copy(status = JobStatus.Running, timeInfo = expectedTimeInfo)
      val responseEntity = HttpEntity(MediaTypes.`application/json`, Json.toJson(expectedEntity))
      mockHttpClient.putExpects(s"$mockAriesBaseUrl/jobs/$jobId", expectedUpdateData, Some(mockAriesCommandCredentials)).returning(Future.successful(HttpResponse(entity = responseEntity)))

      // Send msg
      service ! JobMessage(JobMessageMeta(jobId), JobStarted(startedAt))

      // Verify an info logging msg is published
      // TODO: add logging assertion

      // Stop actor when done
      gracefulStop(service, timeout.duration).futureValue shouldBe true
    }
    "send an Update job Http request and log a warning message if no Job does not exist" in new Scope {
      // Mock http call
      val expectedUpdateData = UpdateJobData(
        status   = Some(JobStatus.Running),
        timeInfo = Some(UpdateTimeInfo(startedAt = Some(startedAt)))
      )
      mockHttpClient.putExpects(s"$mockAriesBaseUrl/jobs/$jobId", expectedUpdateData, Some(mockAriesCommandCredentials)).returning(Future.successful(HttpResponse(StatusCodes.NotFound)))

      // Send msg
      service ! JobMessage(JobMessageMeta(jobId), JobStarted(startedAt))

      // Verify a warning logging msg is published
      // TODO: add logging assertion

      // Stop actor when done
      gracefulStop(service, timeout.duration).futureValue shouldBe true
    }
    "send an Update job Http request and log an error message if there's an Exception when running the Http call" in new Scope {
      // Mock http call
      val expectedUpdateData = UpdateJobData(
        status   = Some(JobStatus.Running),
        timeInfo = Some(UpdateTimeInfo(startedAt = Some(startedAt)))
      )
      val httpException = new RuntimeException("BOOM!")
      mockHttpClient.putExpects(s"$mockAriesBaseUrl/jobs/$jobId", expectedUpdateData, Some(mockAriesCommandCredentials)).returning(Future.failed(httpException))

      // Send msg
      service ! JobMessage(JobMessageMeta(jobId), JobStarted(startedAt))

      // Verify an error logging msg is published
      // TODO: add logging assertion

      // Stop actor when done
      gracefulStop(service, timeout.duration).futureValue shouldBe true
    }
  }

  "When receiving a Heartbeat msg, the service" should {
    "send a create Heartbeat Http request and log an info message if no errors" in new Scope {
      val heartbeatDate = new Date().withoutMillis()
      val currentProgress = 0.1D
      val estimatedTimeRemaining = 2 hours

      // Mock http call
      val expectedUpdateData = HeartbeatData(jobId, heartbeatDate, currentProgress, Some(estimatedTimeRemaining))
      val responseEntity = HttpEntity(MediaTypes.`application/json`, Json.toJson(expectedUpdateData))
      mockHttpClient.postExpects(s"$mockAriesBaseUrl/heartbeats", expectedUpdateData, Some(mockAriesCommandCredentials)).returning(Future.successful(HttpResponse(entity = responseEntity)))

      // Send msg
      service ! JobMessage(JobMessageMeta(jobId), Heartbeat(heartbeatDate, currentProgress, Some(estimatedTimeRemaining)))

      // Verify an info logging msg is published
      // TODO: add logging assertion

      // Stop actor when done
      gracefulStop(service, timeout.duration).futureValue shouldBe true
    }
    "send a create Heartbeat Http request and log an error message if there's an Exception when running the Http call" in new Scope {
      val heartbeatDate = new Date().withoutMillis()
      val currentProgress = 0.1D
      val estimatedTimeRemaining = 2 hours

      // Mock http call
      val expectedUpdateData = HeartbeatData(jobId, heartbeatDate, currentProgress, Some(estimatedTimeRemaining))
      val responseEntity = HttpEntity(MediaTypes.`application/json`, Json.toJson(expectedUpdateData))
      val httpException = new RuntimeException("BOOM!")
      mockHttpClient.postExpects(s"$mockAriesBaseUrl/heartbeats", expectedUpdateData, Some(mockAriesCommandCredentials)).returning(Future.failed(httpException))

      // Send msg
      service ! JobMessage(JobMessageMeta(jobId), Heartbeat(heartbeatDate, currentProgress, Some(estimatedTimeRemaining)))

      // Verify an error logging msg is published
      // TODO: add logging assertion

      // Stop actor when done
      gracefulStop(service, timeout.duration).futureValue shouldBe true
    }
  }

  "When receiving a JobResultSuccess msg, the service" should {
    "send an Update job Http request and log an info message if no errors" in new Scope {
      // Mock http call
      val expectedUpdateData = UpdateJobData(
        status          = Some(JobStatus.Completed),
        timeInfo        = Some(UpdateTimeInfo(completedAt = Some(completedAt))),
        outputPath      = Some(outputPath),
        tasksTimeInfo   = Some(tasksTimeInfo),
        tasksQueuedTime = Some(tasksQueuedTime)
      )
      val expectedTimeInfo = timeInfo.copy(completedAt = Some(completedAt))
      val expectedEntity = jobEntity.copy(
        status          = JobStatus.Completed,
        timeInfo        = expectedTimeInfo,
        tasksTimeInfo   = tasksTimeInfo,
        tasksQueuedTime = Some(tasksQueuedTime)
      )
      val responseEntity = HttpEntity(MediaTypes.`application/json`, Json.toJson(expectedEntity))
      mockHttpClient.putExpects(s"$mockAriesBaseUrl/jobs/$jobId", expectedUpdateData, Some(mockAriesCommandCredentials)).returning(Future.successful(HttpResponse(entity = responseEntity)))

      // Send msg
      service ! JobMessage(JobMessageMeta(jobId), JobResultSuccess(
        completedAt,
        tasksTimeInfo,
        tasksQueuedTime,
        outputPath
      ))

      // Verify an info logging msg is published
      // TODO: add logging assertion

      // Stop actor when done
      gracefulStop(service, timeout.duration).futureValue shouldBe true
    }
    "send an Update job Http request and log a warning message if no Job does not exist" in new Scope {
      // Mock http call
      val expectedUpdateData = UpdateJobData(
        status          = Some(JobStatus.Completed),
        timeInfo        = Some(UpdateTimeInfo(completedAt = Some(completedAt))),
        outputPath      = Some(outputPath),
        tasksTimeInfo   = Some(tasksTimeInfo),
        tasksQueuedTime = Some(tasksQueuedTime)
      )
      val expectedTimeInfo = timeInfo.copy(completedAt = Some(completedAt))
      val expectedEntity = jobEntity.copy(
        status          = JobStatus.Completed,
        timeInfo        = expectedTimeInfo,
        tasksTimeInfo   = tasksTimeInfo,
        tasksQueuedTime = Some(tasksQueuedTime)
      )
      mockHttpClient.putExpects(s"$mockAriesBaseUrl/jobs/$jobId", expectedUpdateData, Some(mockAriesCommandCredentials)).returning(Future.successful(HttpResponse(StatusCodes.NotFound)))

      // Send msg
      service ! JobMessage(JobMessageMeta(jobId), JobResultSuccess(
        completedAt,
        tasksTimeInfo,
        tasksQueuedTime,
        outputPath
      ))

      // Verify a warning logging msg is published
      // TODO: add logging assertion

      // Stop actor when done
      gracefulStop(service, timeout.duration).futureValue shouldBe true
    }
    "send an Update job Http request and log an error message if there's an Exception when running the Http call" in new Scope {
      // Mock http call
      val expectedUpdateData = UpdateJobData(
        status          = Some(JobStatus.Completed),
        timeInfo        = Some(UpdateTimeInfo(completedAt = Some(completedAt))),
        outputPath      = Some(outputPath),
        tasksTimeInfo   = Some(tasksTimeInfo),
        tasksQueuedTime = Some(tasksQueuedTime)
      )
      val httpException = new RuntimeException("BOOM!")
      mockHttpClient.putExpects(s"$mockAriesBaseUrl/jobs/$jobId", expectedUpdateData, Some(mockAriesCommandCredentials)).returning(Future.failed(httpException))

      // Send msg
      service ! JobMessage(JobMessageMeta(jobId), JobResultSuccess(
        completedAt,
        tasksTimeInfo,
        tasksQueuedTime,
        outputPath
      ))

      // Verify an error logging msg is published
      // TODO: add logging assertion

      // Stop actor when done
      gracefulStop(service, timeout.duration).futureValue shouldBe true
    }
  }

  "When receiving a JobResultFailure msg, the service" should {
    "send an Update job Http request and log an info message if no errors" in new Scope {
      val errorCode = "errorCode"
      val errorMessage = "errorMessage"
      val errorDetails = Map("stackTrace" -> "stackTrace")
      // Mock http call
      val expectedUpdateData = UpdateJobData(
        status             = Some(JobStatus.Failed),
        timeInfo           = Some(UpdateTimeInfo(completedAt = Some(completedAt))),
        cortexErrorDetails = Some(CortexErrorDetails(errorCode, errorMessage, errorDetails))
      )
      val expectedTimeInfo = timeInfo.copy(completedAt = Some(completedAt))
      val expectedEntity = jobEntity.copy(
        status             = JobStatus.Failed,
        timeInfo           = expectedTimeInfo,
        cortexErrorDetails = Some(CortexErrorDetails(errorCode, errorMessage, errorDetails))
      )
      val responseEntity = HttpEntity(MediaTypes.`application/json`, Json.toJson(expectedEntity))
      mockHttpClient.putExpects(s"$mockAriesBaseUrl/jobs/$jobId", expectedUpdateData, Some(mockAriesCommandCredentials)).returning(Future.successful(HttpResponse(entity = responseEntity)))

      // Send msg
      service ! JobMessage(JobMessageMeta(jobId), JobResultFailure(
        completedAt,
        errorCode,
        errorMessage,
        errorDetails
      ))

      // Verify an info logging msg is published
      // TODO: add logging assertion

      // Stop actor when done
      gracefulStop(service, timeout.duration).futureValue shouldBe true
    }
  }
}
