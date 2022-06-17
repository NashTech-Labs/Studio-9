package orion.service.job

import akka.actor.ActorRef
import akka.persistence.fsm.PersistentFSM.{ CurrentState, SubscribeTransitionCallBack, Transition }
import cortex.api.job.message._
import orion.domain.service.job.{ JobStatus, _ }
import orion.service.job.JobMessagePublisherWorker._

/**
 * Contains tests of common cases such as 'whenUnhandled' block and a work in general
 */
class JobSupervisorUnitSpec extends JobSupervisorScope {

  import JobSupervisor._
  import JobSupervisorFixtures._

  "When in any state (after job submission) and receiving a CancelJob msg, the JobSupervisor" should {
    "go to WaitingForCancelMsgPublishing state, update retries and publish a CancelJob msg" in new Scope {
      // Submit job
      val jobSubmissionMsg = JobMessage(JobMessageMeta(jobId, Some(jobType)), SubmitJob(inputPath))
      service ! jobSubmissionMsg

      assertCurrentState(WaitingForSubmissionMsgPublishing)
      clearRetries()

      // Send Cancel Job msg
      val cancelJobMsg = JobMessage(JobMessageMeta(jobId, Some(jobType)), CancelJob)
      service ! cancelJobMsg

      stateProbe.expectMsg(Transition(service, WaitingForSubmissionMsgPublishing, WaitingForCancelMsgPublishing, None))
      service ! GetJobData
      fishForSpecificMessage() {
        case JobData(`jobId`, `jobType`, _, _, `messagePublishingRetries`) => succeed
      }
      jobMessagePublisherWorkerProbe.fishForSpecificMessage() {
        case PublishToMasterInQueue(`cancelJobMsg`) => succeed
      }
    }
  }

  "When in any state, job data is not empty and receiving a GetJobStatus msg, the JobSupervisor" should {
    "return a current status of job" in new Scope {
      // Submit job
      val jobSubmissionMsg = JobMessage(JobMessageMeta(jobId, Some(jobType)), SubmitJob(inputPath))
      service ! jobSubmissionMsg

      // Get Job Status
      service ! JobMessage(JobMessageMeta(jobId), GetJobStatus)
      expectMsg(JobStatus.Submitted)
    }
  }

  "When in any state and receiving an unhandled msg, the JobSupervisor" should {
    "do nothing (stay)" in new Scope {
      service ! JobMessage(JobMessageMeta(jobId), GetJobStatus)

      jobMessagePublisherWorkerProbe.expectNoMessage()
      jobMasterAppWorkerProbe.expectNoMessage()
    }
  }

  "When JobSupervisor has been terminated and run again it" should {
    "recover a state if JobSupervisor has the same persistence id" in new Scope {
      // Subscribe to check state transitions
      val expectedState: JobSupervisor.State = WaitingForSubmissionMsgPublishing
      val testPersistenceId = "test-persistence-id"
      val initialService: ActorRef = createService(testPersistenceId)
      // Submit job
      val jobSubmissionMsg = JobMessage(JobMessageMeta(jobId, Some(jobType)), SubmitJob(inputPath))
      initialService ! jobSubmissionMsg

      assertCurrentState(expectedState, initialService)

      // Wait for the service to shutdown
      system.stop(initialService)
      stateProbe.watch(initialService)
      stateProbe.expectTerminated(initialService)

      // Initialize a new service
      val recoveredService: ActorRef = createService(testPersistenceId)

      assertCurrentState(expectedState, recoveredService)
    }
  }
}
