package orion.service.job.supervisor.state

import akka.actor.Status
import akka.persistence.fsm.PersistentFSM.{ StateTimeout, Transition }
import cortex.api.job.message.{ JobMessage, JobMessageMeta, SubmitJob }
import orion.domain.service.job._
import orion.service.job.JobMasterAppWorker.JobMasterAppCreated
import orion.service.job.JobMessagePublisherWorker.MessagePublished
import orion.service.job.{ JobSupervisor, JobSupervisorFixtures, JobSupervisorScope }

class WaitingForJobMasterAppCreationStateUnitSpec extends JobSupervisorScope {

  import JobSupervisor._
  import JobSupervisorFixtures._

  "When in WaitingForJobMasterAppCreation and receiving CreateJobMasterApp msg, the JobSupervisor" should {
    "update its state to WaitingForExecutionStart" in new Scope {
      // Submit job
      val jobSubmissionMsg = JobMessage(JobMessageMeta(jobId, Some(jobType)), SubmitJob(inputPath))
      service ! jobSubmissionMsg
      service ! MessagePublished(jobSubmissionMsg)
      assertCurrentState(WaitingForJobMasterAppCreation)

      service ! JobMasterAppCreated(jobMasterApp)

      stateProbe.expectMsg(Transition(service, WaitingForJobMasterAppCreation, WaitingForExecutionStart, None))
    }
  }

  "When in WaitingForJobMasterAppCreation and receiving Status.Failure msg, the JobSupervisor" should {
    "stop itself" in new Scope {
      // Submit job
      val jobSubmissionMsg = JobMessage(JobMessageMeta(jobId, Some(jobType)), SubmitJob(inputPath))
      service ! jobSubmissionMsg
      service ! MessagePublished(jobSubmissionMsg)
      assertCurrentState(WaitingForJobMasterAppCreation)

      // Verify service is terminated
      watch(service)
      service ! Status.Failure(unrecoverableError)
      expectTerminated(service)
    }
  }

  "When in WaitingForJobMasterAppCreation and receiving StatusTimeout msg, the JobSupervisor" should {
    "update its state to WaitingForExecutionStart" in new Scope {
      // Submit job
      val jobSubmissionMsg = JobMessage(JobMessageMeta(jobId, Some(jobType)), SubmitJob(inputPath))
      service ! jobSubmissionMsg
      service ! MessagePublished(jobSubmissionMsg)
      assertCurrentState(WaitingForJobMasterAppCreation)

      // Send StateTimeout msg
      service ! StateTimeout

      stateProbe.expectMsg(Transition(service, WaitingForJobMasterAppCreation, WaitingForExecutionStart, None))
    }
  }
}
