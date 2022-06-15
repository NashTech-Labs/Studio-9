package aries.service.job

import java.util.{ Date, UUID }

import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.testkit.TestActorRef
import aries.common.json4s.AriesJson4sSupport
import aries.domain.service.job._
import aries.domain.service.{ CreateEntity, DeleteEntity, UpdateEntity }
import aries.testkit.service.ServiceBaseSpec

import scala.concurrent.Future
import scala.concurrent.duration._

class JobCommandServiceUnitSpec extends ServiceBaseSpec {

  // Fixtures
  val jobId = UUID.randomUUID()
  val submitted = new Date()
  val ownerId = UUID.randomUUID()
  val jobType = "TRAIN"
  val jobStatus = JobStatus.Submitted
  val inputPath = "some/input/path"
  val startedAt = new Date().withoutMillis()
  val completedAt = new Date().withoutMillis()
  val createJobData = CreateJobData(jobId, submitted, ownerId, jobType, jobStatus, inputPath)
  val updatedStatus = JobStatus.Completed
  val timeInfo = TimeInfo(submitted, None, None)
  val outputPath = "some/output/path"
  val updateTimeInfo = UpdateTimeInfo(None, Some(startedAt), Some(completedAt))
  val updateTasksTimeInfo = TimeInfo(submitted, Some(startedAt), Some(completedAt))
  val tasksTimeInfo = Seq(TaskTimeInfo("task1", updateTasksTimeInfo), TaskTimeInfo("task2", updateTasksTimeInfo))
  val tasksQueuedTime = 20 minutes
  val updateJobData = UpdateJobData(
    status            = Some(updatedStatus),
    time_info         = Some(updateTimeInfo),
    tasks_queued_time = Some(tasksQueuedTime),
    tasks_time_info   = Some(tasksTimeInfo),
    output_path       = Some(outputPath)
  )
  val jobEntity = JobEntity(jobId, ownerId, jobType, jobStatus, inputPath, timeInfo)

  val mockAriesBaseUrl = "http://api.aries.com/v1"
  val mockAriesCommandCredentials = BasicHttpCredentials("mock-command-username", "mock-command-password")
  val mockAriesRequestRetryCount = 0

  trait Scope extends ServiceScope with AriesJson4sSupport {
    val mockRepository = mock[JobRepository]

    val service = TestActorRef(new JobCommandService {
      override val repository = mockRepository
    })
  }

  "When receiving a CreateEntity msg, the service" should {
    "respond with the created Job if no errors" in new Scope {
      // Mock Repository call
      (mockRepository.create _).expects(jobEntity).returning(Future.successful(jobEntity))

      // Send msg
      service ! CreateEntity(createJobData)

      // Verify service response
      expectMsg(jobEntity)
    }
    "respond with a Failure msg if the call to ElasticSearch fails" in new Scope {
      val elasticException = new RuntimeException("BOOM!")

      // Mock Repository call
      (mockRepository.create _).expects(jobEntity).returning(Future.failed(elasticException))

      // Send msg
      service ! CreateEntity(createJobData)

      // Verify service response
      expectMsgFailure(elasticException)
    }
  }

  "When receiving a UpdateEntity msg, the service" should {
    "respond with the updated Job if no errors" in new Scope {
      val updatedTimeInfo = timeInfo.copy(
        started_at   = Some(startedAt),
        completed_at = Some(completedAt)
      )
      val updatedEntity = jobEntity.copy(
        status            = updatedStatus,
        time_info         = updatedTimeInfo,
        tasks_queued_time = Some(tasksQueuedTime),
        tasks_time_info   = tasksTimeInfo,
        output_path       = Some(outputPath)
      )

      // Mock Repository calls
      (mockRepository.retrieve _).expects(jobId).returning(Future.successful(Some(jobEntity)))
      (mockRepository.update _).expects(jobId, updatedEntity).returning(Future.successful(Some(updatedEntity)))

      // Send msg
      service ! UpdateEntity(jobId, updateJobData)

      // Verify service response
      expectMsg(Some(updatedEntity))
    }
    "respond with the updated Job with cortex error details" in new Scope {
      val updatedJobEntity: JobEntity = jobEntity.copy(
        cortex_error_details = Some(CortexErrorDetails("ec-101", "message", Map("st" -> "trace")))
      )

      val jobEntityWithCortexError: JobEntity = jobEntity.copy(
        cortex_error_details = Some(CortexErrorDetails("ec-101", "message", Map("st" -> "trace")))
      )

      val updateJobDataWithCortexError: UpdateJobData = UpdateJobData(
        cortex_error_details = Some(CortexErrorDetails("ec-101", "message", Map("st" -> "trace")))
      )
      // Mock Repository calls
      (mockRepository.retrieve _).expects(jobId).returning(Future.successful(Some(jobEntityWithCortexError)))
      (mockRepository.update _).expects(jobId, updatedJobEntity).returning(Future.successful(Some(updatedJobEntity)))

      // Send msg
      service ! UpdateEntity(jobId, updateJobDataWithCortexError)

      // Verify service response
      expectMsg(Some(updatedJobEntity))
    }
    "respond with a None msg if the Job does not exist" in new Scope {
      // Mock Repository calls
      (mockRepository.retrieve _).expects(jobId).returning(Future.successful(None))
      (mockRepository.update _).expects(*, *).never()

      // Send msg
      service ! UpdateEntity(jobId, updateJobData)

      // Verify service response
      expectMsg(None)
    }
    "respond with a Failure msg if the call to ElasticSearch for retrieving the Job fails" in new Scope {
      val elasticException = new RuntimeException("BOOM!")

      // Mock Repository call
      (mockRepository.retrieve _).expects(jobId).returning(Future.failed(elasticException))
      (mockRepository.update _).expects(*, *).never()

      // Send msg
      service ! UpdateEntity(jobId, updateJobData)

      // Verify service response
      expectMsgFailure(elasticException)
    }
    "respond with a Failure msg if the call to ElasticSearch for updating the Job fails" in new Scope {
      val updatedTimeInfo = timeInfo.copy(
        started_at   = Some(startedAt),
        completed_at = Some(completedAt)
      )
      val updatedEntity = jobEntity.copy(
        status            = updatedStatus,
        time_info         = updatedTimeInfo,
        tasks_queued_time = Some(tasksQueuedTime),
        tasks_time_info   = tasksTimeInfo,
        output_path       = Some(outputPath)
      )

      val elasticException = new RuntimeException("BOOM!")

      // Mock Repository call
      (mockRepository.retrieve _).expects(jobId).returning(Future.successful(Some(jobEntity)))
      (mockRepository.update _).expects(jobId, updatedEntity).returning(Future.failed(elasticException))

      // Send msg
      service ! UpdateEntity(jobId, updateJobData)

      // Verify service response
      expectMsgFailure(elasticException)
    }
  }

  "When receiving a DeleteEntity msg, the service" should {
    "update the JobStatus to Cancelled if it exists" in new Scope {
      val updatedEntity = jobEntity.copy(
        status = JobStatus.Cancelled
      )

      // Mock Repository call
      (mockRepository.retrieve _).expects(jobId).returning(Future.successful(Some(jobEntity)))
      (mockRepository.update _).expects(jobId, updatedEntity).returning(Future.successful(Some(jobEntity)))

      // Send msg
      service ! DeleteEntity(jobId)

      // Verify service response
      expectMsgPF() {
        case Some(jobEntity: JobEntity) =>
          jobEntity.id shouldBe jobId
      }
    }
    "respond with None if a Job for the given Id does not exist" in new Scope {
      val nonExistentId = UUID.randomUUID()

      // Mock Repository call
      (mockRepository.retrieve _).expects(nonExistentId).returning(Future.successful(None))
      (mockRepository.update _).expects(*, *).never()

      // Send msg
      service ! DeleteEntity(nonExistentId)

      // Verify service response
      expectMsg(None)
    }
    "respond with a Failure msg if the call to ElasticSearch for retrieving the Job fails" in new Scope {
      val elasticException = new RuntimeException("BOOM!")

      // Mock Repository call
      (mockRepository.retrieve _).expects(jobId).returning(Future.failed(elasticException))
      (mockRepository.update _).expects(*, *).never()

      // Send msg
      service ! DeleteEntity(jobId)

      // Verify service response
      expectMsgFailure(elasticException)
    }
    "respond with a Failure msg if the call to ElasticSearch for updating the Job fails" in new Scope {
      val updatedEntity = jobEntity.copy(
        status = JobStatus.Cancelled
      )

      val elasticException = new RuntimeException("BOOM!")

      // Mock Repository call
      (mockRepository.retrieve _).expects(jobId).returning(Future.successful(Some(jobEntity)))
      (mockRepository.update _).expects(jobId, updatedEntity).returning(Future.failed(elasticException))

      // Send msg
      service ! DeleteEntity(jobId)

      // Verify service response
      expectMsgFailure(elasticException)
    }
  }
}
