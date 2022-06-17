package orion.service.job.supervisor.state

import akka.actor.Status
import akka.persistence.fsm.PersistentFSM.Transition
import orion.domain.service.job._
import orion.service.job.JobMasterAppWorker.JobMasterAppCreated
import orion.service.job.JobMessagePublisherWorker.MessagePublished
import akka.persistence.fsm.PersistentFSM.StateTimeout
import cortex.api.job.message._
import orion.service.job.{ JobSupervisor, JobSupervisorFixtures, JobSupervisorScope }

class WaitingForJobStartedMsgPublishingStateUnitSpec extends JobSupervisorScope {

  import JobSupervisor._
  import JobSupervisorFixtures._

  "When in WaitingForJobStartedMsgPublishing and receiving MessagePublished msg, the JobSupervisor" should {
    "update its state to WaitingForExecutionResult" in new Scope {
      // Submit job
      val jobSubmissionMsg = JobMessage(JobMessageMeta(jobId, Some(jobType)), SubmitJob(inputPath))
      val heartbeatMsg = JobMessage(JobMessageMeta(jobId), Heartbeat(created, currentProgress, Some(estimatedTimeRemaining)))
      service ! jobSubmissionMsg
      service ! MessagePublished(jobSubmissionMsg)
      service ! JobMasterAppCreated(jobMasterApp)
      service ! heartbeatMsg
      assertCurrentState(WaitingForJobStartedMsgPublishing)

      val jobStartedMsg = JobMessage(JobMessageMeta(jobId), JobStarted(created))
      service ! MessagePublished(jobStartedMsg)

      stateProbe.expectMsg(Transition(service, WaitingForJobStartedMsgPublishing, WaitingForExecutionResult, None))
    }
  }

  "When in WaitingForJobStartedMsgPublishing and receiving Status.Failure msg, the JobSupervisor" should {
    "update its state to WaitingForExecutionResult" in new Scope {
      // Submit job
      val jobSubmissionMsg = JobMessage(JobMessageMeta(jobId, Some(jobType)), SubmitJob(inputPath))
      val heartbeatMsg = JobMessage(JobMessageMeta(jobId), Heartbeat(created, currentProgress, Some(estimatedTimeRemaining)))
      service ! jobSubmissionMsg
      service ! MessagePublished(jobSubmissionMsg)
      service ! JobMasterAppCreated(jobMasterApp)
      service ! heartbeatMsg
      assertCurrentState(WaitingForJobStartedMsgPublishing)

      service ! Status.Failure(unrecoverableError)

      stateProbe.expectMsg(Transition(service, WaitingForJobStartedMsgPublishing, WaitingForExecutionResult, None))
    }
  }

  "When in WaitingForJobStartedMsgPublishing and receiving StatusTimeout msg, the JobSupervisor" should {
    "update its state to WaitingForExecutionResult" in new Scope {
      // Submit job
      val jobSubmissionMsg = JobMessage(JobMessageMeta(jobId, Some(jobType)), SubmitJob(inputPath))
      val heartbeatMsg = JobMessage(JobMessageMeta(jobId), Heartbeat(created, currentProgress, Some(estimatedTimeRemaining)))
      service ! jobSubmissionMsg
      service ! MessagePublished(jobSubmissionMsg)
      service ! JobMasterAppCreated(jobMasterApp)
      service ! heartbeatMsg
      assertCurrentState(WaitingForJobStartedMsgPublishing)

      service ! StateTimeout

      stateProbe.expectMsg(Transition(service, WaitingForJobStartedMsgPublishing, WaitingForExecutionResult, None))
    }
  }
}
