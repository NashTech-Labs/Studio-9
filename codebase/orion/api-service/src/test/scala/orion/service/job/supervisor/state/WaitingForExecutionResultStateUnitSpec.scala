package orion.service.job.supervisor.state

import akka.persistence.fsm.PersistentFSM.{ StateTimeout, Transition }
import cortex.api.job.message._
import orion.domain.service.job._
import orion.service.job.JobMasterAppWorker.JobMasterAppCreated
import orion.service.job.JobMessagePublisherWorker.{ MessagePublished, PublishToStatusQueue }
import orion.service.job.{ JobSupervisor, JobSupervisorFixtures, JobSupervisorScope }

import scala.reflect.classTag

class WaitingForExecutionResultStateUnitSpec extends JobSupervisorScope {

  import JobSupervisor._
  import JobSupervisorFixtures._

  "When in WaitingForExecutionResult and receiving Heartbeat msg, the JobSupervisor" should {
    "send Heartbeat msg and stay in the same state" in new Scope {
      val jobSubmissionMsg = JobMessage(JobMessageMeta(jobId, Some(jobType)), SubmitJob(inputPath))
      val heartbeatMsg = JobMessage(JobMessageMeta(jobId), Heartbeat(created, currentProgress, Some(estimatedTimeRemaining)))
      val jobStartedMsg = JobMessage(JobMessageMeta(jobId), JobStarted(created))
      service ! jobSubmissionMsg
      service ! MessagePublished(jobSubmissionMsg)
      service ! JobMasterAppCreated(jobMasterApp)
      service ! heartbeatMsg
      service ! MessagePublished(jobStartedMsg)
      assertCurrentState(WaitingForExecutionResult)

      // Get Job Status
      service ! JobMessage(JobMessageMeta(jobId), GetJobStatus)
      val statusBefore: JobStatus = expectMsgType(classTag[JobStatus])

      // Send Heartbeat msg
      service ! heartbeatMsg

      jobMessagePublisherWorkerProbe.fishForSpecificMessage() {
        case PublishToStatusQueue(`heartbeatMsg`) => succeed
      }
      // Get Job Status
      service ! JobMessage(JobMessageMeta(jobId), GetJobStatus)
      val statusAfter: JobStatus = expectMsgType(classTag[JobStatus])

      assertCurrentState(WaitingForExecutionResult)
      statusBefore shouldBe statusAfter
    }
  }

  "When in WaitingForExecutionResult and receiving JobResultSuccess msg, the JobSupervisor" should {
    "update its state to WaitingForResultMsgPublishing, update retries, change a job status and send JobResultSuccess msg" in new Scope {
      val jobSubmissionMsg = JobMessage(JobMessageMeta(jobId, Some(jobType)), SubmitJob(inputPath))
      val heartbeatMsg = JobMessage(JobMessageMeta(jobId), Heartbeat(created, currentProgress, Some(estimatedTimeRemaining)))
      val jobStartedMsg = JobMessage(JobMessageMeta(jobId), JobStarted(created))
      service ! jobSubmissionMsg
      service ! MessagePublished(jobSubmissionMsg)
      service ! JobMasterAppCreated(jobMasterApp)
      service ! heartbeatMsg
      service ! MessagePublished(jobStartedMsg)
      assertCurrentState(WaitingForExecutionResult)
      clearRetries()

      // Send Job Result msg
      val jobResultMsg = JobMessage(JobMessageMeta(jobId, Some(jobType)), JobResultSuccess(completedAt, tasksTimeInfo, tasksQueuedTime, outputPath))
      service ! jobResultMsg

      stateProbe.expectMsg(Transition(service, WaitingForExecutionResult, WaitingForResultMsgPublishing, None))
      service ! GetJobData
      fishForSpecificMessage() {
        case JobData(`jobId`, `jobType`, JobStatus.Succeeded, _, `messagePublishingRetries`) => succeed
      }
      jobMessagePublisherWorkerProbe.fishForSpecificMessage() {
        case PublishToStatusQueue(`jobResultMsg`) => succeed
      }
    }
  }

  "When in WaitingForExecutionResult and receiving JobResultFailure msg, the JobSupervisor" should {
    "update its state to WaitingForResultMsgPublishing, update retries, change a job status and send JobResultFailure msg" in new Scope {
      val jobSubmissionMsg = JobMessage(JobMessageMeta(jobId, Some(jobType)), SubmitJob(inputPath))
      val heartbeatMsg = JobMessage(JobMessageMeta(jobId), Heartbeat(created, currentProgress, Some(estimatedTimeRemaining)))
      val jobStartedMsg = JobMessage(JobMessageMeta(jobId), JobStarted(created))
      service ! jobSubmissionMsg
      service ! MessagePublished(jobSubmissionMsg)
      service ! JobMasterAppCreated(jobMasterApp)
      service ! heartbeatMsg
      service ! MessagePublished(jobStartedMsg)
      assertCurrentState(WaitingForExecutionResult)
      clearRetries()

      // Send Job Result msg
      val jobResultMsg = JobMessage(JobMessageMeta(jobId, Some(jobType)), JobResultFailure(completedAt, outputPath, "Some error message"))
      service ! jobResultMsg

      stateProbe.expectMsg(Transition(service, WaitingForExecutionResult, WaitingForResultMsgPublishing, None))
      service ! GetJobData
      fishForSpecificMessage() {
        case JobData(`jobId`, `jobType`, JobStatus.Failed, _, `messagePublishingRetries`) => succeed
      }
      jobMessagePublisherWorkerProbe.fishForSpecificMessage() {
        case PublishToStatusQueue(`jobResultMsg`) => succeed
      }
    }
  }

  "When in WaitingForExecutionResult and receiving StateTimeout msg, the JobSupervisor" should {
    "update its state to WaitingForResultMsgPublishing, update retries, change a job status and send JobResultFailure msg" in new Scope {
      val jobSubmissionMsg = JobMessage(JobMessageMeta(jobId, Some(jobType)), SubmitJob(inputPath))
      val heartbeatMsg = JobMessage(JobMessageMeta(jobId), Heartbeat(created, currentProgress, Some(estimatedTimeRemaining)))
      val jobStartedMsg = JobMessage(JobMessageMeta(jobId), JobStarted(created))
      service ! jobSubmissionMsg
      service ! MessagePublished(jobSubmissionMsg)
      service ! JobMasterAppCreated(jobMasterApp)
      service ! heartbeatMsg
      service ! MessagePublished(jobStartedMsg)
      assertCurrentState(WaitingForExecutionResult)
      clearRetries()

      // Send StateTimeout msg
      service ! StateTimeout

      stateProbe.expectMsg(Transition(service, WaitingForExecutionResult, WaitingForResultMsgPublishing, None))
      service ! GetJobData
      fishForSpecificMessage() {
        case JobData(`jobId`, `jobType`, JobStatus.Failed, _, `messagePublishingRetries`) => succeed
      }
      jobMessagePublisherWorkerProbe.fishForSpecificMessage() {
        case PublishToStatusQueue(JobMessage(JobMessageMeta(`jobId`, Some(`jobType`)), JobResultFailure(`mockCurrentDate`, _, _, _))) => succeed
      }
    }
  }
}
