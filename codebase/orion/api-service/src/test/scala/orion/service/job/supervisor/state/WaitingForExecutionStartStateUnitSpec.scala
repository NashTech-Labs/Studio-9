package orion.service.job.supervisor.state

import akka.actor.Status
import akka.persistence.fsm.PersistentFSM.{ StateTimeout, Transition }
import cortex.api.job.message._
import orion.common.service.MarathonClient.AppStatus
import orion.domain.service.job._
import orion.service.job.JobMasterAppWorker.{ GetJobMasterAppStatus, GetJobMasterAppStatusResult, JobMasterAppCreated }
import orion.service.job.JobMessagePublisherWorker.{ MessagePublished, PublishToStatusQueue }
import orion.service.job.{ JobSupervisor, JobSupervisorFixtures, JobSupervisorScope }

import scala.reflect.classTag

class WaitingForExecutionStartStateUnitSpec extends JobSupervisorScope {

  import JobSupervisor._
  import JobSupervisorFixtures._

  "When in WaitingForExecutionStart and receiving Heartbeat msg, the JobSupervisor" should {
    "update its state to WaitingForJobStartedMsgPublishing, change a job status and send JobStarted msg " in new Scope {
      // Submit job
      val jobSubmissionMsg = JobMessage(JobMessageMeta(jobId, Some(jobType)), SubmitJob(inputPath))
      service ! jobSubmissionMsg
      service ! MessagePublished(jobSubmissionMsg)
      service ! JobMasterAppCreated(jobMasterApp)
      assertCurrentState(WaitingForExecutionStart)

      // Send Heartbeat msg
      val heartbeatMsg = JobMessage(JobMessageMeta(jobId), Heartbeat(created, currentProgress, Some(estimatedTimeRemaining)))
      service ! heartbeatMsg

      stateProbe.expectMsg(Transition(service, WaitingForExecutionStart, WaitingForJobStartedMsgPublishing, None))
      // Get Job Status
      service ! JobMessage(JobMessageMeta(jobId), GetJobStatus)
      expectMsg(JobStatus.Running)
      jobMessagePublisherWorkerProbe.fishForSpecificMessage() {
        case PublishToStatusQueue(JobMessage(JobMessageMeta(`jobId`, _), JobStarted(`created`))) => succeed
      }
    }
  }

  "When in WaitingForExecutionStart and receiving StateTimeout msg, the JobSupervisor" should {
    "send GetJobMasterAppStatus msg and stay in the same state" in new Scope {
      // Submit job
      val jobSubmissionMsg = JobMessage(JobMessageMeta(jobId, Some(jobType)), SubmitJob(inputPath))
      service ! jobSubmissionMsg
      service ! MessagePublished(jobSubmissionMsg)
      service ! JobMasterAppCreated(jobMasterApp)
      assertCurrentState(WaitingForExecutionStart)

      // Get Job Status
      service ! JobMessage(JobMessageMeta(jobId), GetJobStatus)
      val statusBeforeTimeout: JobStatus = expectMsgType(classTag[JobStatus])

      // Send StateTimeout msg
      service ! StateTimeout

      jobMasterAppWorkerProbe.fishForSpecificMessage() {
        case GetJobMasterAppStatus(`jobId`) => succeed
      }
      // Get Job Status
      service ! JobMessage(JobMessageMeta(jobId), GetJobStatus)
      val statusAfterTimeout: JobStatus = expectMsgType(classTag[JobStatus])

      assertCurrentState(WaitingForExecutionStart)
      statusBeforeTimeout shouldBe statusAfterTimeout
    }
  }

  "When in WaitingForExecutionStart and receiving GetJobMasterAppStatusResult (Running status) msg, the JobSupervisor" should {
    "update its state to WaitingForResultMsgPublishing, update retries, change a job status and send JobResultFailure msg " in new Scope {
      // Submit job
      val jobSubmissionMsg = JobMessage(JobMessageMeta(jobId, Some(jobType)), SubmitJob(inputPath))
      service ! jobSubmissionMsg
      service ! MessagePublished(jobSubmissionMsg)
      service ! JobMasterAppCreated(jobMasterApp)
      assertCurrentState(WaitingForExecutionStart)
      clearRetries()

      // Send GetJobMasterAppStatusResult msg
      val getJobMasterAppStatusResultMsg = GetJobMasterAppStatusResult(Some(AppStatus.Running))
      service ! getJobMasterAppStatusResultMsg

      stateProbe.expectMsg(Transition(service, WaitingForExecutionStart, WaitingForResultMsgPublishing, None))
      service ! GetJobData
      fishForSpecificMessage() {
        case JobData(`jobId`, `jobType`, JobStatus.Failed, _, `messagePublishingRetries`) => succeed
      }
      jobMessagePublisherWorkerProbe.fishForSpecificMessage() {
        case PublishToStatusQueue(JobMessage(JobMessageMeta(`jobId`, Some(`jobType`)), JobResultFailure(`mockCurrentDate`, _, _, _))) => succeed
      }
    }
  }

  "When in WaitingForExecutionStart and receiving GetJobMasterAppStatusResult (not Running status) msg, the JobSupervisor" should {
    "stay in the same state" in new Scope {
      // Submit job
      val jobSubmissionMsg = JobMessage(JobMessageMeta(jobId, Some(jobType)), SubmitJob(inputPath))
      service ! jobSubmissionMsg
      service ! MessagePublished(jobSubmissionMsg)
      service ! JobMasterAppCreated(jobMasterApp)
      assertCurrentState(WaitingForExecutionStart)

      // Get Job Status
      service ! JobMessage(JobMessageMeta(jobId), GetJobStatus)
      val statusBefore: JobStatus = expectMsgType(classTag[JobStatus])

      // Send GetJobMasterAppStatusResult msg
      val getJobMasterAppStatusResultMsg = GetJobMasterAppStatusResult(Some(AppStatus.Delayed))
      service ! getJobMasterAppStatusResultMsg

      // Get Job Status
      service ! JobMessage(JobMessageMeta(jobId), GetJobStatus)
      val statusAfter: JobStatus = expectMsgType(classTag[JobStatus])

      assertCurrentState(WaitingForExecutionStart)
      statusBefore shouldBe statusAfter
    }
  }

  "When in WaitingForExecutionStart and receiving GetJobMasterAppStatusResult (empty status) msg, the JobSupervisor" should {
    "update its state to WaitingForResultMsgPublishing, update retries, change a job status and send JobResultFailure msg " in new Scope {
      // Submit job
      val jobSubmissionMsg = JobMessage(JobMessageMeta(jobId, Some(jobType)), SubmitJob(inputPath))
      service ! jobSubmissionMsg
      service ! MessagePublished(jobSubmissionMsg)
      service ! JobMasterAppCreated(jobMasterApp)
      assertCurrentState(WaitingForExecutionStart)
      clearRetries()

      // Send GetJobMasterAppStatusResult msg
      val getJobMasterAppStatusResultMsg = GetJobMasterAppStatusResult(None)
      service ! getJobMasterAppStatusResultMsg

      stateProbe.expectMsg(Transition(service, WaitingForExecutionStart, WaitingForResultMsgPublishing, None))
      service ! GetJobData
      fishForSpecificMessage() {
        case JobData(`jobId`, `jobType`, JobStatus.Failed, _, `messagePublishingRetries`) => succeed
      }
      jobMessagePublisherWorkerProbe.fishForSpecificMessage() {
        case PublishToStatusQueue(JobMessage(JobMessageMeta(`jobId`, Some(`jobType`)), JobResultFailure(`mockCurrentDate`, _, _, _))) => succeed
      }
    }
  }

  "When in WaitingForExecutionStart and receiving Status.Failure msg, the JobSupervisor" should {
    "stop itself" in new Scope {
      // Submit job
      val jobSubmissionMsg = JobMessage(JobMessageMeta(jobId, Some(jobType)), SubmitJob(inputPath))
      service ! jobSubmissionMsg
      service ! MessagePublished(jobSubmissionMsg)
      service ! JobMasterAppCreated(jobMasterApp)
      assertCurrentState(WaitingForExecutionStart)

      // Verify service is terminated
      watch(service)
      service ! Status.Failure(unrecoverableError)
      expectTerminated(service)
    }
  }
}
