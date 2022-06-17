package cortex.service.job

import java.util.{ Date, UUID }

import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.http.scaladsl.model.{ HttpEntity, HttpResponse, MediaTypes, StatusCodes }
import akka.testkit.TestActorRef
import cortex.api.job.message.TimeInfo
import cortex.common.json4s.{ CortexJson4sSupport, Json }
import cortex.domain.service.job._
import cortex.domain.service.{ ListEntities, RetrieveEntity }
import cortex.testkit.service.ServiceBaseSpec

import scala.concurrent.Future
import scala.concurrent.duration._

class JobQueryServiceUnitSpec extends ServiceBaseSpec {

  // Fixtures
  val jobId = UUID.randomUUID()
  val ownerId = UUID.randomUUID()
  val submittedAt = new Date().withoutMillis()
  val jobType = "TRAIN"
  val jobStatus = JobStatus.Submitted
  val inputPath = "some/input/path"
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

  val heartbeatId = UUID.randomUUID()
  val createdHeartbeatDate = new Date().withoutMillis()
  val currentProgress = 0.45D
  val estimatedTimeRemaining = 2 hours
  val heartbeat = HeartbeatEntity(heartbeatId, jobId, createdHeartbeatDate, currentProgress, Some(estimatedTimeRemaining))

  val mockAriesBaseUrl = "http://api.aries.com/v1"
  val mockAriesSearchCredentials = BasicHttpCredentials("mock-search-username", "mock-search-password")
  val mockAriesRequestRetryCount = 0

  trait Scope extends ServiceScope with CortexJson4sSupport {
    val service = TestActorRef(new JobQueryService with DateSupportTesting with UUIDSupportTesting with HttpClientSupportTesting {
      override val ariesBaseUrl = mockAriesBaseUrl
      override val ariesQueryCredentials = mockAriesSearchCredentials
      override val ariesRequestRetryCount = mockAriesRequestRetryCount
    })
  }

  "When sending a RetrieveEntity msg, the service" should {
    "respond with the requested Entity if it exists" in new Scope {
      val id = jobId

      // Mock http call
      val responseEntity = HttpEntity(MediaTypes.`application/json`, Json.toJson(jobEntity))
      mockHttpClient.getExpects(s"$mockAriesBaseUrl/jobs/$jobId", Some(mockAriesSearchCredentials)).returning(Future.successful(HttpResponse(entity = responseEntity)))

      // Send msg
      service ! RetrieveEntity(id)

      // Verify service response
      expectMsg(Some(jobEntity))
    }
    "respond with the None if requested Entity does not exist" in new Scope {
      val nonExistentId = UUID.randomUUID()

      // Mock http call
      mockHttpClient.getExpects(s"$mockAriesBaseUrl/jobs/$nonExistentId", Some(mockAriesSearchCredentials)).returning(Future.successful(HttpResponse(StatusCodes.NotFound)))

      // Send msg
      service ! RetrieveEntity(nonExistentId)

      // Verify service response
      expectMsg(None)
    }
    "respond with a Failure msg if the call for retrieving the Job fails" in new Scope {
      val httpException = new RuntimeException("BOOM!")

      // Mock http call
      mockHttpClient.getExpects(s"$mockAriesBaseUrl/jobs/$jobId", Some(mockAriesSearchCredentials)).returning(Future.failed(httpException))

      // Send msg
      service ! RetrieveEntity(jobId)

      // Verify service response
      expectMsgFailure(httpException)
    }
  }

  "When sending a GetJobStatus msg, the service" should {
    "respond with the requested JobStatus if it exists" in new Scope {
      val jobStatus = JobStatus.Submitted
      val jobStatusData = JobStatusData(jobStatus, None, None, None)

      // Mock http calls
      val jobResponseEntity = HttpEntity(MediaTypes.`application/json`, Json.toJson(jobEntity.copy(status = jobStatus)))
      mockHttpClient
        .getExpects(s"$mockAriesBaseUrl/jobs/$jobId", Some(mockAriesSearchCredentials))
        .returning(Future.successful(HttpResponse(entity = jobResponseEntity)))

      // Send msg
      service ! GetJobStatus(jobId)

      // Verify service response
      expectMsg(Some(jobStatusData))
    }
    "respond with the error details if them exist" in new Scope {
      val cortexErrorDetails = Some(CortexErrorDetails("errorCode", "errorMessage", Map("stackTrace" -> "stackTrace")))
      val jobStatusData = JobStatusData(jobStatus, None, None, cortexErrorDetails)

      // Mock http calls
      val jobResponseEntity = HttpEntity(MediaTypes.`application/json`, Json.toJson(jobEntity.copy(cortexErrorDetails = cortexErrorDetails)))
      mockHttpClient
        .getExpects(s"$mockAriesBaseUrl/jobs/$jobId", Some(mockAriesSearchCredentials))
        .returning(Future.successful(HttpResponse(entity = jobResponseEntity)))

      // Send msg
      service ! GetJobStatus(jobId)

      // Verify service response
      expectMsg(Some(jobStatusData))
    }
    "respond with None if a Job for the requested Id does not exist" in new Scope {
      val nonExistentId = UUID.randomUUID()

      // Mock http call
      mockHttpClient
        .getExpects(s"$mockAriesBaseUrl/jobs/$nonExistentId", Some(mockAriesSearchCredentials))
        .returning(Future.successful(HttpResponse(StatusCodes.NotFound)))

      // Send msg
      service ! GetJobStatus(nonExistentId)

      // Verify service response
      expectMsg(None)
    }
    "respond with the requested JobStatus with progress data if in Running status" in new Scope {
      val jobStatus = JobStatus.Running
      val jobStatusData = JobStatusData(jobStatus, Some(currentProgress), Some(estimatedTimeRemaining), None)

      // Mock http calls
      val jobResponseEntity = HttpEntity(MediaTypes.`application/json`, Json.toJson(jobEntity.copy(status = jobStatus)))
      mockHttpClient
        .getExpects(s"$mockAriesBaseUrl/jobs/$jobId", Some(mockAriesSearchCredentials))
        .returning(Future.successful(HttpResponse(entity = jobResponseEntity)))

      val heartbeatResponseEntity = HttpEntity(MediaTypes.`application/json`, Json.toJson(Seq(heartbeat)))
      mockHttpClient
        .getExpects(s"$mockAriesBaseUrl/heartbeats/latest?jobId=$jobId", Some(mockAriesSearchCredentials))
        .returning(Future.successful(HttpResponse(entity = heartbeatResponseEntity)))

      // Send msg
      service ! GetJobStatus(jobId)

      // Verify service response
      expectMsg(Some(jobStatusData))
    }
    "respond with empty progress and estimate values if no Heartbeat is found for the requested Id" in new Scope {
      val jobStatus = JobStatus.Running
      val jobStatusData = JobStatusData(jobStatus, None, None, None)

      // Mock http calls
      val jobResponseEntity = HttpEntity(MediaTypes.`application/json`, Json.toJson(jobEntity.copy(status = jobStatus)))
      mockHttpClient
        .getExpects(s"$mockAriesBaseUrl/jobs/$jobId", Some(mockAriesSearchCredentials))
        .returning(Future.successful(HttpResponse(entity = jobResponseEntity)))

      val heartbeatResponseEntity = HttpEntity(MediaTypes.`application/json`, Json.toJson(Seq()))
      mockHttpClient
        .getExpects(s"$mockAriesBaseUrl/heartbeats/latest?jobId=$jobId", Some(mockAriesSearchCredentials))
        .returning(Future.successful(HttpResponse(entity = heartbeatResponseEntity)))

      // Send msg
      service ! GetJobStatus(jobId)

      // Verify service response
      expectMsgPF() {
        case Some(JobStatusData(`jobStatus`, None, None, None)) => succeed
      }
    }
    "respond with empty estimated time remaining if there's a Heartbeat for the requested Id, but the value is empty" in new Scope {
      val jobStatus = JobStatus.Running
      val jobStatusData = JobStatusData(jobStatus, None, None, None)

      // Mock http calls
      val jobResponseEntity = HttpEntity(MediaTypes.`application/json`, Json.toJson(jobEntity.copy(status = jobStatus)))
      mockHttpClient
        .getExpects(s"$mockAriesBaseUrl/jobs/$jobId", Some(mockAriesSearchCredentials))
        .returning(Future.successful(HttpResponse(entity = jobResponseEntity)))

      val heartbeatResponseEntity = HttpEntity(MediaTypes.`application/json`, Json.toJson(Seq(heartbeat.copy(estimatedTimeRemaining = None))))
      mockHttpClient
        .getExpects(s"$mockAriesBaseUrl/heartbeats/latest?jobId=$jobId", Some(mockAriesSearchCredentials))
        .returning(Future.successful(HttpResponse(entity = heartbeatResponseEntity)))

      // Send msg
      service ! GetJobStatus(jobId)

      // Verify service response
      expectMsgPF() {
        case Some(JobStatusData(`jobStatus`, Some(`currentProgress`), None, None)) => succeed
      }
    }
    "respond with a Failure msg if the call for retrieving the Job fails" in new Scope {
      val httpException = new RuntimeException("BOOM!")

      // Mock http call
      mockHttpClient
        .getExpects(s"$mockAriesBaseUrl/jobs/$jobId", Some(mockAriesSearchCredentials))
        .returning(Future.failed(httpException))

      // Send msg
      service ! GetJobStatus(jobId)

      // Verify service response
      expectMsgFailure(httpException)
    }
    "respond with a Failure msg if the call for retrieving the last Heartbeat fails" in new Scope {
      // TODO: should be respond with empty progress and estimate instead of failure here?
      val jobStatus = JobStatus.Running
      val httpException = new RuntimeException("BOOM!")

      // Mock http calls
      val jobResponseEntity = HttpEntity(MediaTypes.`application/json`, Json.toJson(jobEntity.copy(status = jobStatus)))
      mockHttpClient
        .getExpects(s"$mockAriesBaseUrl/jobs/$jobId", Some(mockAriesSearchCredentials))
        .returning(Future.successful(HttpResponse(entity = jobResponseEntity)))

      mockHttpClient
        .getExpects(s"$mockAriesBaseUrl/heartbeats/latest?jobId=$jobId", Some(mockAriesSearchCredentials))
        .returning(Future.failed(httpException))

      // Send msg
      service ! GetJobStatus(jobId)

      // Verify service response
      expectMsgFailure(httpException)
    }
  }

  "When sending a FindJobQuery msg, the service" should {
    "respond with the matching Entities if filtering by ownerId" in new Scope {
      val requestedOwner = ownerId
      val searchCriteria = JobSearchCriteria(owner = Some(requestedOwner))

      // Mock http call
      val entities = Seq(jobEntity.copy(owner = ownerId))
      val responseEntity = HttpEntity(MediaTypes.`application/json`, Json.toJson(entities))
      mockHttpClient.postExpects(s"$mockAriesBaseUrl/jobs/search", searchCriteria, Some(mockAriesSearchCredentials)).returning(Future.successful(HttpResponse(entity = responseEntity)))

      // Send msg
      service ! FindJob(searchCriteria)

      // Verify service response
      expectMsg(entities)
    }
    "respond with the matching Entities if filtering by job type" in new Scope {
      val requestedJobType = "TRAIN"
      val searchCriteria = JobSearchCriteria(jobType = Some(requestedJobType))

      // Mock http call
      val entities = Seq(jobEntity.copy(jobType = requestedJobType))
      val responseEntity = HttpEntity(MediaTypes.`application/json`, Json.toJson(entities))
      mockHttpClient.postExpects(s"$mockAriesBaseUrl/jobs/search", searchCriteria, Some(mockAriesSearchCredentials)).returning(Future.successful(HttpResponse(entity = responseEntity)))

      // Send msg
      service ! FindJob(searchCriteria)

      // Verify service response
      expectMsg(entities)
    }
    "respond with the matching Entities if filtering by job status" in new Scope {
      val requestedJobStatus = JobStatus.Running
      val searchCriteria = JobSearchCriteria(status = Some(requestedJobStatus))

      // Mock http call
      val entities = Seq(jobEntity.copy(status = requestedJobStatus))
      val responseEntity = HttpEntity(MediaTypes.`application/json`, Json.toJson(entities))
      mockHttpClient.postExpects(s"$mockAriesBaseUrl/jobs/search", searchCriteria, Some(mockAriesSearchCredentials)).returning(Future.successful(HttpResponse(entity = responseEntity)))

      // Send msg
      service ! FindJob(searchCriteria)

      // Verify service response
      expectMsg(entities)
    }
  }

  "When sending a ListEntities msg, the service" should {
    "respond with all the known Users" in new Scope {
      // Mock http call
      val entities = Seq(jobEntity)
      val responseEntity = HttpEntity(MediaTypes.`application/json`, Json.toJson(entities))
      mockHttpClient.getExpects(s"$mockAriesBaseUrl/jobs", Some(mockAriesSearchCredentials)).returning(Future.successful(HttpResponse(entity = responseEntity)))

      // Send msg
      service ! ListEntities

      // Verify service response
      expectMsg(entities)
    }
  }
}
