package orion.service.job.supervisor.state

import akka.persistence.fsm.PersistentFSM.Transition
import com.spingo.op_rabbit.Message.Ack
import cortex.api.job.message._
import orion.domain.service.job._
import orion.service.job.JobMessagePublisherWorker.{ MessagePublished, PublishToCleanUpResourcesQueue }
import orion.service.job.{ JobSupervisor, JobSupervisorFixtures, JobSupervisorScope }

class WaitingForJobMasterAppTerminationStateUnitSpec extends JobSupervisorScope {

  import JobSupervisor._
  import JobSupervisorFixtures._

  "When in WaitingForJobMasterAppTermination state and receiving JobMasterAppReadyForTermination msg, the JobSupervisor" should {
    "update its state to WaitingForResourcesCleanUpMsgPublishing, update retries and send CleanUpResources msg" in new Scope {
      val jobSubmissionMsg = JobMessage(JobMessageMeta(jobId, Some(jobType)), SubmitJob(inputPath))
      val cancelJobMsg = JobMessage(JobMessageMeta(jobId, Some(jobType)), CancelJob)
      service ! jobSubmissionMsg
      service ! cancelJobMsg
      service ! MessagePublished(cancelJobMsg)
      assertCurrentState(WaitingForJobMasterAppTermination)
      clearRetries()

      val readyForTerminationMsg = JobMessage(JobMessageMeta(jobId, Some(jobType)), JobMasterAppReadyForTermination)
      service ! readyForTerminationMsg

      stateProbe.expectMsg(Transition(service, WaitingForJobMasterAppTermination, WaitingForResourcesCleanUpMsgPublishing, None))
      service ! GetJobData
      fishForSpecificMessage() {
        case JobData(`jobId`, `jobType`, _, _, `messagePublishingRetries`) => succeed
      }
      jobMessagePublisherWorkerProbe.fishForSpecificMessage() {
        case PublishToCleanUpResourcesQueue(JobMessage(JobMessageMeta(`jobId`, Some(`jobType`)), CleanUpResources)) => succeed
      }
    }
  }
}
