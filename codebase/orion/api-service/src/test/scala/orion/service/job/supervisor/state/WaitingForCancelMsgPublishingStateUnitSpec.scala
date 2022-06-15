package orion.service.job.supervisor.state

import akka.actor.Status
import akka.persistence.fsm.PersistentFSM.{ StateTimeout, Transition }
import cortex.api.job.message._
import orion.domain.service.job._
import orion.service.job.JobMessagePublisherWorker.{ MessagePublished, PublishToCleanUpResourcesQueue, PublishToMasterInQueue, PublishToStatusQueue }
import orion.service.job.{ JobSupervisor, JobSupervisorFixtures, JobSupervisorScope }

import scala.reflect.classTag

class WaitingForCancelMsgPublishingStateUnitSpec extends JobSupervisorScope {

  import JobSupervisor._
  import JobSupervisorFixtures._

  "When in WaitingForCancelMsgPublishing and receiving MessagePublished msg, the JobSupervisor" should {
    "update its state to WaitingForJobMasterAppTermination and change a job status" in new Scope {
      val jobSubmissionMsg = JobMessage(JobMessageMeta(jobId, Some(jobType)), SubmitJob(inputPath))
      val cancelJobMsg = JobMessage(JobMessageMeta(jobId, Some(jobType)), CancelJob)
      service ! jobSubmissionMsg
      service ! cancelJobMsg
      assertCurrentState(WaitingForCancelMsgPublishing)

      service ! MessagePublished(cancelJobMsg)

      stateProbe.expectMsg(Transition(service, WaitingForCancelMsgPublishing, WaitingForJobMasterAppTermination, None))
      // Get Job Status
      service ! JobMessage(JobMessageMeta(jobId), GetJobStatus)
      expectMsg(JobStatus.Cancelled)
    }
  }

  "When in WaitingForCancelMsgPublishing and receiving Status.Failure msg, the JobSupervisor" should {
    "stay in the same state, decrease retries and send CancelJob msg again if retries aren't over" in new Scope {
      val jobSubmissionMsg = JobMessage(JobMessageMeta(jobId, Some(jobType)), SubmitJob(inputPath))
      val cancelJobMsg = JobMessage(JobMessageMeta(jobId, Some(jobType)), CancelJob)
      service ! jobSubmissionMsg
      service ! cancelJobMsg
      assertCurrentState(WaitingForCancelMsgPublishing)
      setRetries()

      // Get Job Status
      service ! JobMessage(JobMessageMeta(jobId), GetJobStatus)
      val statusBefore: JobStatus = expectMsgType(classTag[JobStatus])

      service ! Status.Failure(unrecoverableError)

      // Get Job Status
      service ! JobMessage(JobMessageMeta(jobId), GetJobStatus)
      val statusAfter: JobStatus = expectMsgType(classTag[JobStatus])

      assertCurrentState(WaitingForCancelMsgPublishing)
      statusBefore shouldBe statusAfter
      service ! GetJobData
      fishForSpecificMessage() {
        case JobData(`jobId`, `jobType`, _, _, `decreasedRetries`) => succeed
      }
      jobMessagePublisherWorkerProbe.fishForSpecificMessage() {
        case PublishToMasterInQueue(`cancelJobMsg`) => succeed
      }
    }
  }

  "When in WaitingForCancelMsgPublishing and receiving Status.Failure msg, the JobSupervisor" should {
    "stop itself if retries are over" in new Scope {
      val jobSubmissionMsg = JobMessage(JobMessageMeta(jobId, Some(jobType)), SubmitJob(inputPath))
      val cancelJobMsg = JobMessage(JobMessageMeta(jobId, Some(jobType)), CancelJob)
      service ! jobSubmissionMsg
      service ! cancelJobMsg
      assertCurrentState(WaitingForCancelMsgPublishing)

      clearRetries()

      watch(service)
      service ! Status.Failure(unrecoverableError)
      expectTerminated(service)
    }
  }

  "When in WaitingForCancelMsgPublishing and receiving StateTimeout msg, the JobSupervisor" should {
    "stay in the same state, decrease retries and send CancelJob msg again if retries aren't over" in new Scope {
      val jobSubmissionMsg = JobMessage(JobMessageMeta(jobId, Some(jobType)), SubmitJob(inputPath))
      val cancelJobMsg = JobMessage(JobMessageMeta(jobId, Some(jobType)), CancelJob)
      service ! jobSubmissionMsg
      service ! cancelJobMsg
      assertCurrentState(WaitingForCancelMsgPublishing)
      setRetries()

      // Get Job Status
      service ! JobMessage(JobMessageMeta(jobId), GetJobStatus)
      val statusBefore: JobStatus = expectMsgType(classTag[JobStatus])

      service ! StateTimeout

      // Get Job Status
      service ! JobMessage(JobMessageMeta(jobId), GetJobStatus)
      val statusAfter: JobStatus = expectMsgType(classTag[JobStatus])

      assertCurrentState(WaitingForCancelMsgPublishing)
      statusBefore shouldBe statusAfter
      service ! GetJobData
      fishForSpecificMessage() {
        case JobData(`jobId`, `jobType`, _, _, `decreasedRetries`) => succeed
      }
      jobMessagePublisherWorkerProbe.fishForSpecificMessage() {
        case PublishToMasterInQueue(`cancelJobMsg`) => succeed
      }
    }
  }

  "When in WaitingForCancelMsgPublishing and receiving StateTimeout msg, the JobSupervisor" should {
    "stop itself if retries are over" in new Scope {
      val jobSubmissionMsg = JobMessage(JobMessageMeta(jobId, Some(jobType)), SubmitJob(inputPath))
      val cancelJobMsg = JobMessage(JobMessageMeta(jobId, Some(jobType)), CancelJob)
      service ! jobSubmissionMsg
      service ! cancelJobMsg
      assertCurrentState(WaitingForCancelMsgPublishing)

      clearRetries()

      watch(service)
      service ! StateTimeout
      expectTerminated(service)
    }
  }

  "When in WaitingForCancelMsgPublishing and receiving JobMasterAppReadyForTermination msg, the JobSupervisor" should {
    "update its state to WaitingForJobMasterAppTermination, update retries and send CleanUpResources msg" in new Scope {
      val jobSubmissionMsg = JobMessage(JobMessageMeta(jobId, Some(jobType)), SubmitJob(inputPath))
      val cancelJobMsg = JobMessage(JobMessageMeta(jobId, Some(jobType)), CancelJob)
      service ! jobSubmissionMsg
      service ! cancelJobMsg
      assertCurrentState(WaitingForCancelMsgPublishing)
      clearRetries()

      // Get Job Status
      service ! JobMessage(JobMessageMeta(jobId), GetJobStatus)
      val statusBefore: JobStatus = expectMsgType(classTag[JobStatus])

      val readyForTerminationMsg = JobMessage(JobMessageMeta(jobId, Some(jobType)), JobMasterAppReadyForTermination)
      service ! readyForTerminationMsg

      // Get Job Status
      service ! JobMessage(JobMessageMeta(jobId), GetJobStatus)
      val statusAfter: JobStatus = expectMsgType(classTag[JobStatus])

      statusBefore shouldBe statusAfter
      stateProbe.expectMsg(Transition(service, WaitingForCancelMsgPublishing, WaitingForResourcesCleanUpMsgPublishing, None))
      service ! GetJobData
      fishForSpecificMessage() {
        case JobData(`jobId`, `jobType`, JobStatus.Submitted, _, `messagePublishingRetries`) => succeed
      }
      jobMessagePublisherWorkerProbe.fishForSpecificMessage() {
        case PublishToCleanUpResourcesQueue(JobMessage(JobMessageMeta(`jobId`, Some(`jobType`)), CleanUpResources)) => succeed
      }
    }
  }

  "When in WaitingForCancelMsgPublishing and receiving JobResultSuccess msg, the JobSupervisor" should {
    "update its state to WaitingForResultMsgPublishing, update retries, change a job status and send JobResultSuccess msg" in new Scope {
      val jobSubmissionMsg = JobMessage(JobMessageMeta(jobId, Some(jobType)), SubmitJob(inputPath))
      val cancelJobMsg = JobMessage(JobMessageMeta(jobId, Some(jobType)), CancelJob)
      service ! jobSubmissionMsg
      service ! cancelJobMsg
      assertCurrentState(WaitingForCancelMsgPublishing)
      clearRetries()

      // Send Job Result msg
      val jobResultMsg = JobMessage(JobMessageMeta(jobId, Some(jobType)), JobResultSuccess(completedAt, tasksTimeInfo, tasksQueuedTime, outputPath))
      service ! jobResultMsg

      stateProbe.expectMsg(Transition(service, WaitingForCancelMsgPublishing, WaitingForResultMsgPublishing, None))
      service ! GetJobData
      fishForSpecificMessage() {
        case JobData(`jobId`, `jobType`, JobStatus.Succeeded, _, `messagePublishingRetries`) => succeed
      }
      jobMessagePublisherWorkerProbe.fishForSpecificMessage() {
        case PublishToStatusQueue(`jobResultMsg`) => succeed
      }
    }
  }

  "When in WaitingForCancelMsgPublishing and receiving JobResultFailure msg, the JobSupervisor" should {
    "update its state to WaitingForResultMsgPublishing, update retries, change a job status and send JobResultFailure msg" in new Scope {
      val jobSubmissionMsg = JobMessage(JobMessageMeta(jobId, Some(jobType)), SubmitJob(inputPath))
      val cancelJobMsg = JobMessage(JobMessageMeta(jobId, Some(jobType)), CancelJob)
      service ! jobSubmissionMsg
      service ! cancelJobMsg
      assertCurrentState(WaitingForCancelMsgPublishing)
      clearRetries()

      // Send Job Result msg
      val jobResultMsg = JobMessage(JobMessageMeta(jobId, Some(jobType)), JobResultFailure(completedAt, outputPath, "Some error message"))
      service ! jobResultMsg

      stateProbe.expectMsg(Transition(service, WaitingForCancelMsgPublishing, WaitingForResultMsgPublishing, None))
      service ! GetJobData
      fishForSpecificMessage() {
        case JobData(`jobId`, `jobType`, JobStatus.Failed, _, `messagePublishingRetries`) => succeed
      }
      jobMessagePublisherWorkerProbe.fishForSpecificMessage() {
        case PublishToStatusQueue(`jobResultMsg`) => succeed
      }
    }
  }
}
