package orion.service.job.supervisor.state

import akka.actor.Status
import akka.persistence.fsm.PersistentFSM.StateTimeout
import cortex.api.job.message._
import orion.domain.service.job._
import orion.service.job.JobMasterAppWorker.JobMasterAppCreated
import orion.service.job.JobMessagePublisherWorker.{ MessagePublished, PublishToCleanUpResourcesQueue }
import orion.service.job.{ JobSupervisor, JobSupervisorFixtures, JobSupervisorScope }

import scala.reflect.classTag

class WaitingForResourcesCleanUpMsgPublishingStateUnitSpec extends JobSupervisorScope {

  import JobSupervisor._
  import JobSupervisorFixtures._

  "When in WaitingForResourcesCleanUpMsgPublishing and receiving MessagePublished msg, the JobSupervisor" should {
    "stop itself" in new Scope {
      val jobSubmissionMsg = JobMessage(JobMessageMeta(jobId, Some(jobType)), SubmitJob(inputPath))
      val heartbeatMsg = JobMessage(JobMessageMeta(jobId), Heartbeat(created, currentProgress, Some(estimatedTimeRemaining)))
      val jobStartedMsg = JobMessage(JobMessageMeta(jobId), JobStarted(created))
      val jobResultMsg = JobMessage(JobMessageMeta(jobId, Some(jobType)), JobResultSuccess(completedAt, tasksTimeInfo, tasksQueuedTime, outputPath))
      service ! jobSubmissionMsg
      service ! MessagePublished(jobSubmissionMsg)
      service ! JobMasterAppCreated(jobMasterApp)
      service ! heartbeatMsg
      service ! MessagePublished(jobStartedMsg)
      service ! jobResultMsg
      service ! MessagePublished(jobResultMsg)
      assertCurrentState(WaitingForResourcesCleanUpMsgPublishing)

      val cleanUpResourcesMsg = JobMessage(JobMessageMeta(jobId, Some(jobType)), CleanUpResources)
      service ! MessagePublished(cleanUpResourcesMsg)

      // Verify service is terminated
      watch(service)
      service ! Status.Failure(unrecoverableError)
      expectTerminated(service)
    }
  }

  "When in WaitingForResourcesCleanUpMsgPublishing and receiving Status.Failure msg, the JobSupervisor" should {
    "stay in the same state, decrease retries and send CleanUpResources msg again if retries aren't over" in new Scope {
      val jobSubmissionMsg = JobMessage(JobMessageMeta(jobId, Some(jobType)), SubmitJob(inputPath))
      val heartbeatMsg = JobMessage(JobMessageMeta(jobId), Heartbeat(created, currentProgress, Some(estimatedTimeRemaining)))
      val jobStartedMsg = JobMessage(JobMessageMeta(jobId), JobStarted(created))
      val jobResultMsg = JobMessage(JobMessageMeta(jobId, Some(jobType)), JobResultSuccess(completedAt, tasksTimeInfo, tasksQueuedTime, outputPath))
      service ! jobSubmissionMsg
      service ! MessagePublished(jobSubmissionMsg)
      service ! JobMasterAppCreated(jobMasterApp)
      service ! heartbeatMsg
      service ! MessagePublished(jobStartedMsg)
      service ! jobResultMsg
      service ! MessagePublished(jobResultMsg)
      assertCurrentState(WaitingForResourcesCleanUpMsgPublishing)
      setRetries()

      // Get Job Status
      service ! JobMessage(JobMessageMeta(jobId), GetJobStatus)
      val statusBefore: JobStatus = expectMsgType(classTag[JobStatus])

      service ! Status.Failure(unrecoverableError)

      // Get Job Status
      service ! JobMessage(JobMessageMeta(jobId), GetJobStatus)
      val statusAfter: JobStatus = expectMsgType(classTag[JobStatus])

      assertCurrentState(WaitingForResourcesCleanUpMsgPublishing)
      statusBefore shouldBe statusAfter
      service ! GetJobData
      fishForSpecificMessage() {
        case JobData(`jobId`, `jobType`, _, _, `decreasedRetries`) => succeed
      }
      jobMessagePublisherWorkerProbe.fishForSpecificMessage() {
        case PublishToCleanUpResourcesQueue(JobMessage(JobMessageMeta(`jobId`, Some(`jobType`)), CleanUpResources)) => succeed
      }
    }
  }

  "When in WaitingForResourcesCleanUpMsgPublishing and receiving Status.Failure msg, the JobSupervisor" should {
    "stop itself if retries are over" in new Scope {
      val jobSubmissionMsg = JobMessage(JobMessageMeta(jobId, Some(jobType)), SubmitJob(inputPath))
      val heartbeatMsg = JobMessage(JobMessageMeta(jobId), Heartbeat(created, currentProgress, Some(estimatedTimeRemaining)))
      val jobStartedMsg = JobMessage(JobMessageMeta(jobId), JobStarted(created))
      val jobResultMsg = JobMessage(JobMessageMeta(jobId, Some(jobType)), JobResultSuccess(completedAt, tasksTimeInfo, tasksQueuedTime, outputPath))
      service ! jobSubmissionMsg
      service ! MessagePublished(jobSubmissionMsg)
      service ! JobMasterAppCreated(jobMasterApp)
      service ! heartbeatMsg
      service ! MessagePublished(jobStartedMsg)
      service ! jobResultMsg
      service ! MessagePublished(jobResultMsg)
      assertCurrentState(WaitingForResourcesCleanUpMsgPublishing)

      clearRetries()

      watch(service)
      service ! Status.Failure(unrecoverableError)
      expectTerminated(service)
    }
  }

  "When in WaitingForResourcesCleanUpMsgPublishing and receiving StateTimeout msg, the JobSupervisor" should {
    "stay in the same state and send CleanUpResources msg again if retries aren't over" in new Scope {
      val jobSubmissionMsg = JobMessage(JobMessageMeta(jobId, Some(jobType)), SubmitJob(inputPath))
      val heartbeatMsg = JobMessage(JobMessageMeta(jobId), Heartbeat(created, currentProgress, Some(estimatedTimeRemaining)))
      val jobStartedMsg = JobMessage(JobMessageMeta(jobId), JobStarted(created))
      val jobResultMsg = JobMessage(JobMessageMeta(jobId, Some(jobType)), JobResultSuccess(completedAt, tasksTimeInfo, tasksQueuedTime, outputPath))
      service ! jobSubmissionMsg
      service ! MessagePublished(jobSubmissionMsg)
      service ! JobMasterAppCreated(jobMasterApp)
      service ! heartbeatMsg
      service ! MessagePublished(jobStartedMsg)
      service ! jobResultMsg
      service ! MessagePublished(jobResultMsg)
      assertCurrentState(WaitingForResourcesCleanUpMsgPublishing)
      setRetries()

      // Get Job Status
      service ! JobMessage(JobMessageMeta(jobId), GetJobStatus)
      val statusBefore: JobStatus = expectMsgType(classTag[JobStatus])

      service ! StateTimeout

      // Get Job Status
      service ! JobMessage(JobMessageMeta(jobId), GetJobStatus)
      val statusAfter: JobStatus = expectMsgType(classTag[JobStatus])

      assertCurrentState(WaitingForResourcesCleanUpMsgPublishing)
      statusBefore shouldBe statusAfter
      service ! GetJobData
      fishForSpecificMessage() {
        case JobData(`jobId`, `jobType`, _, _, `decreasedRetries`) => succeed
      }
      jobMessagePublisherWorkerProbe.fishForSpecificMessage() {
        case PublishToCleanUpResourcesQueue(JobMessage(JobMessageMeta(`jobId`, Some(`jobType`)), CleanUpResources)) => succeed
      }
    }
  }

  "When in WaitingForResourcesCleanUpMsgPublishing and receiving StateTimeout msg, the JobSupervisor" should {
    "stop itself if retries are over" in new Scope {
      val jobSubmissionMsg = JobMessage(JobMessageMeta(jobId, Some(jobType)), SubmitJob(inputPath))
      val heartbeatMsg = JobMessage(JobMessageMeta(jobId), Heartbeat(created, currentProgress, Some(estimatedTimeRemaining)))
      val jobStartedMsg = JobMessage(JobMessageMeta(jobId), JobStarted(created))
      val jobResultMsg = JobMessage(JobMessageMeta(jobId, Some(jobType)), JobResultSuccess(completedAt, tasksTimeInfo, tasksQueuedTime, outputPath))
      service ! jobSubmissionMsg
      service ! MessagePublished(jobSubmissionMsg)
      service ! JobMasterAppCreated(jobMasterApp)
      service ! heartbeatMsg
      service ! MessagePublished(jobStartedMsg)
      service ! jobResultMsg
      service ! MessagePublished(jobResultMsg)
      assertCurrentState(WaitingForResourcesCleanUpMsgPublishing)

      clearRetries()

      watch(service)
      service ! StateTimeout
      expectTerminated(service)
    }
  }
}
