package orion.service.job.supervisor.state

import akka.actor.Status
import akka.persistence.fsm.PersistentFSM.{ StateTimeout, Transition }
import cortex.api.job.message._
import orion.domain.service.job._
import orion.service.job.JobMasterAppWorker.CreateJobMasterApp
import orion.service.job.JobMessagePublisherWorker.MessagePublished
import orion.service.job.{ JobSupervisor, JobSupervisorFixtures, JobSupervisorScope }

class WaitingForSubmissionMsgPublishingStateUnitSpec extends JobSupervisorScope {

  import JobSupervisor._
  import JobSupervisorFixtures._

  "When in WaitingForSubmissionMsgPublishing and receiving MessagePublished msg, the JobSupervisor" should {
    "update its state to WaitingForJobMasterAppCreation, change a job status and send CreateJobMasterApp msg " in new Scope {
      // Submit job
      val jobSubmissionMsg = JobMessage(JobMessageMeta(jobId, Some(jobType)), SubmitJob(inputPath))
      service ! jobSubmissionMsg
      assertCurrentState(WaitingForSubmissionMsgPublishing)

      service ! MessagePublished(jobSubmissionMsg)

      stateProbe.expectMsg(Transition(service, WaitingForSubmissionMsgPublishing, WaitingForJobMasterAppCreation, None))
      // Get Job Status
      service ! JobMessage(JobMessageMeta(jobId), GetJobStatus)
      expectMsg(JobStatus.Submitted)
      jobMasterAppWorkerProbe.expectMsg(CreateJobMasterApp(jobId))
    }
  }

  "When in WaitingForSubmissionMsgPublishing and receiving Status.Failure msg, the JobSupervisor" should {
    "stop itself" in new Scope {
      // Submit job
      val jobSubmissionMsg = JobMessage(JobMessageMeta(jobId, Some(jobType)), SubmitJob(inputPath))
      service ! jobSubmissionMsg
      assertCurrentState(WaitingForSubmissionMsgPublishing)

      // Verify service is terminated
      watch(service)
      service ! Status.Failure(unrecoverableError)
      expectTerminated(service)
    }
  }

  "When in WaitingForSubmissionMsgPublishing and receiving StatusTimeout msg, the JobSupervisor" should {
    "update its state to WaitingForJobMasterAppCreation, change a job status and send CreateJobMasterApp msg " in new Scope {
      // Submit job
      val jobSubmissionMsg = JobMessage(JobMessageMeta(jobId, Some(jobType)), SubmitJob(inputPath))
      service ! jobSubmissionMsg
      assertCurrentState(WaitingForSubmissionMsgPublishing)

      // Send StateTimeout msg
      service ! StateTimeout

      stateProbe.expectMsg(Transition(service, WaitingForSubmissionMsgPublishing, WaitingForJobMasterAppCreation, None))
      // Get Job Status
      service ! JobMessage(JobMessageMeta(jobId), GetJobStatus)
      expectMsg(JobStatus.Submitted)
      jobMasterAppWorkerProbe.expectMsg(CreateJobMasterApp(jobId))
    }
  }
}
