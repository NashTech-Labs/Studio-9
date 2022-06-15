package orion.service.job.supervisor.state

import akka.persistence.fsm.PersistentFSM.Transition
import cortex.api.job.message._
import orion.domain.service.job._
import orion.service.job.JobMessagePublisherWorker.PublishToMasterInQueue
import orion.service.job.{ JobSupervisor, JobSupervisorFixtures, JobSupervisorScope }

class IdleStateUnitSpec extends JobSupervisorScope {

  import JobSupervisor._
  import JobSupervisorFixtures._

  "When in Idle state and receiving SubmitJob msg, the JobSupervisor" should {
    "update its state to WaitingForSubmissionMsgPublishing, change a job status and send msg for publishing SubmitJob msg" in new Scope {
      assertCurrentState(Idle)

      // Submit job
      val jobSubmissionMsg = JobMessage(JobMessageMeta(jobId, Some(jobType)), SubmitJob(inputPath))
      service ! jobSubmissionMsg

      stateProbe.expectMsg(Transition(service, Idle, WaitingForSubmissionMsgPublishing, None))

      // Get Job Status
      service ! JobMessage(JobMessageMeta(jobId), GetJobStatus)
      expectMsg(JobStatus.Submitted)

      jobMessagePublisherWorkerProbe.expectMsg(PublishToMasterInQueue(jobSubmissionMsg))
    }
  }
}
