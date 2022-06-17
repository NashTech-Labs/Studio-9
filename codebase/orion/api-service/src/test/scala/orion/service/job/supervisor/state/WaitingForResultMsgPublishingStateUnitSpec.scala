package orion.service.job.supervisor.state

import akka.actor.Status
import akka.persistence.fsm.PersistentFSM.{ StateTimeout, Transition }
import cortex.api.job.message._
import orion.service.job.JobMasterAppWorker.JobMasterAppCreated
import orion.service.job.JobMessagePublisherWorker.{ MessagePublished, PublishToCleanUpResourcesQueue, PublishToStatusQueue }
import orion.service.job.{ JobSupervisor, JobSupervisorFixtures, JobSupervisorScope }

class WaitingForResultMsgPublishingStateUnitSpec extends JobSupervisorScope {

  import JobSupervisor._
  import JobSupervisorFixtures._

  "When in WaitingForResultMsgPublishing and receiving MessagePublished msg, the JobSupervisor" should {
    "update its state to WaitingForResourcesCleanUpMsgPublishing, update retries and send CleanUpResources msg " in new Scope {
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
      assertCurrentState(WaitingForResultMsgPublishing)
      clearRetries()

      service ! MessagePublished(jobResultMsg)

      stateProbe.expectMsg(Transition(service, WaitingForResultMsgPublishing, WaitingForResourcesCleanUpMsgPublishing, None))
      service ! GetJobData
      fishForSpecificMessage() {
        case JobData(`jobId`, `jobType`, _, _, `messagePublishingRetries`) => succeed
      }
      jobMessagePublisherWorkerProbe.fishForSpecificMessage() {
        case PublishToCleanUpResourcesQueue(JobMessage(JobMessageMeta(`jobId`, Some(`jobType`)), CleanUpResources)) => succeed
      }
    }
  }

  "When in WaitingForResultMsgPublishing and receiving Status.Failure msg, the JobSupervisor" should {
    "go to the same state, decrease retries and send JobResult msg again" in new Scope {
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
      assertCurrentState(WaitingForResultMsgPublishing)
      setRetries()

      service ! Status.Failure(unrecoverableError)
      stateProbe.expectMsg(Transition(service, WaitingForResultMsgPublishing, WaitingForResultMsgPublishing, None))
      service ! GetJobData
      fishForSpecificMessage() {
        case JobData(`jobId`, `jobType`, _, _, `decreasedRetries`) => succeed
      }
      jobMessagePublisherWorkerProbe.fishForSpecificMessage() {
        case PublishToStatusQueue(`jobResultMsg`) => succeed
      }
    }
  }

  "When in WaitingForResultMsgPublishing and receiving Status.Failure msg, the JobSupervisor" should {
    "update its state to WaitingForResourcesCleanUpMsgPublishing, update retries and send CleanUpResources msg again if retries are over" in new Scope {
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
      assertCurrentState(WaitingForResultMsgPublishing)
      clearState()
      clearRetries()

      service ! Status.Failure(unrecoverableError)

      assertCurrentState(WaitingForResourcesCleanUpMsgPublishing)
      service ! GetJobData
      fishForSpecificMessage() {
        case JobData(`jobId`, `jobType`, _, _, `messagePublishingRetries`) => succeed
      }
      jobMessagePublisherWorkerProbe.fishForSpecificMessage() {
        case PublishToCleanUpResourcesQueue(JobMessage(JobMessageMeta(`jobId`, Some(`jobType`)), CleanUpResources)) => succeed
      }
    }
  }

  "When in WaitingForResultMsgPublishing and receiving StateTimeout msg, the JobSupervisor" should {
    "go to the same state, decrease retries and send JobResult msg again" in new Scope {
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
      assertCurrentState(WaitingForResultMsgPublishing)
      setRetries()

      service ! StateTimeout
      stateProbe.expectMsg(Transition(service, WaitingForResultMsgPublishing, WaitingForResultMsgPublishing, None))
      service ! GetJobData
      fishForSpecificMessage() {
        case JobData(`jobId`, `jobType`, _, _, `decreasedRetries`) => succeed
      }
      jobMessagePublisherWorkerProbe.fishForSpecificMessage() {
        case PublishToStatusQueue(`jobResultMsg`) => succeed
      }
    }
  }

  "When in WaitingForResultMsgPublishing and receiving StateTimeout msg, the JobSupervisor" should {
    "update its state to WaitingForResourcesCleanUpMsgPublishing, update retries and send CleanUpResources msg again if retries are over" in new Scope {
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
      assertCurrentState(WaitingForResultMsgPublishing)
      clearState()
      clearRetries()

      service ! StateTimeout

      assertCurrentState(WaitingForResourcesCleanUpMsgPublishing)
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
