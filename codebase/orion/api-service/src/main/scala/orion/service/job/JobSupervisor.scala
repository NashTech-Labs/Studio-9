package orion.service.job

import java.util.{ Date, UUID }

import akka.actor.{ ActorRef, Props, Status }
import akka.cluster.sharding.ShardRegion
import akka.persistence.fsm.PersistentFSM.FSMState
import cortex.api.job.message._
import orion.common.service.{ DateSupport, MarathonClient, NamedActor, Service }
import orion.common.utils.ExtendedPersistentFSM
import orion.domain.service.job._

import scala.concurrent.duration.FiniteDuration
import scala.reflect.{ ClassTag, _ }

object JobSupervisor extends NamedActor {
  val Name = "job-supervisor"

  def props(): Props = Props(new JobSupervisor())

  // States
  sealed trait State extends FSMState

  case object Idle extends State {
    override def identifier: String = "idle"
  }

  case object WaitingForSubmissionMsgPublishing extends State {
    override def identifier: String = "waiting-for-submission-msg-publishing"
  }

  case object WaitingForJobMasterAppCreation extends State {
    override def identifier: String = "waiting-for-job-master-app-creation"
  }

  case object WaitingForJobStartedMsgPublishing extends State {
    override def identifier: String = "waiting-for-job-started-msg-publishing"
  }

  case object WaitingForExecutionStart extends State {
    override def identifier: String = "waiting-for-execution-start"
  }

  case object WaitingForExecutionResult extends State {
    override def identifier: String = "waiting-for-execution-result"
  }

  case object WaitingForResultMsgPublishing extends State {
    override def identifier: String = "waiting-for-result-msg-publishing"
  }

  case object WaitingForResourcesCleanUpMsgPublishing extends State {
    override def identifier: String = "waiting-for-resources-clean-up-msg-publishing"
  }

  case object WaitingForCancelMsgPublishing extends State {
    override def identifier: String = "waiting-for-cancel-msg-publishing"
  }

  case object WaitingForJobMasterAppTermination extends State {
    override def identifier: String = "waiting-for-job-master-app-termination"
  }

  // Data
  sealed trait Data
  case object EmptyData extends Data
  case class JobData(jobId: UUID, jobType: String, status: JobStatus, result: Option[JobResult] = None, publishRetriesLeft: Int = 0) extends Data

  // Events
  sealed trait DomainEvent
  case class JobSupervisorInitializedEvent(jobId: UUID, jobType: String) extends DomainEvent
  case object JobExecutionStartedEvent extends DomainEvent
  case class JobExecutionSucceedEvent(result: JobResultSuccess) extends DomainEvent
  case class JobExecutionFailedEvent(result: JobResultFailure) extends DomainEvent
  case object JobCancelledEvent extends DomainEvent
  case object MessagePublishingEvent extends DomainEvent
  case object MessagePublishingRetryEvent extends DomainEvent

  object Sharding {
    val extractEntityId: ShardRegion.ExtractEntityId = {
      case msg @ JobMessage(JobMessageMeta(jobId, _), _) => (jobId.toString, msg)
    }

    val extractShardId: ShardRegion.ExtractShardId = {
      case JobMessage(JobMessageMeta(jobId, _), _) => (math.abs(jobId.toString.hashCode) % 100).toString
    }
  }

}

class JobSupervisor extends Service
    with ExtendedPersistentFSM[JobSupervisor.State, JobSupervisor.Data, JobSupervisor.DomainEvent]
    with DateSupport {

  import JobMasterAppWorker._
  import JobMessagePublisherWorker._
  import JobSupervisor._

  private val jobSupervisorSettings = JobSupervisorSettings(context.system)
  private val jobMasterStartTimeout: FiniteDuration = jobSupervisorSettings.jobMasterStartTimeout
  private val jobMasterHeartbeatTimeout: FiniteDuration = jobSupervisorSettings.jobMasterHeartbeatTimeout
  private val messagePublishingTimeout: FiniteDuration = jobSupervisorSettings.messagePublishingTimeout
  private val messagePublishingRetries: Int = jobSupervisorSettings.messagePublishingRetries

  log.info("[ JobId: {} ] - Starting JobSupervisor at [{}]", self.path.name, self)

  override def persistenceId: String = {
    // Note:
    // self.path.parent.parent.name is the ShardRegion actor name: job-supervisor
    // self.path.parent.name is the Shard supervisor actor name: 5
    // self.path.name is the sharded Entity actor name: 75430231-b920-4811-8950-dad9ca18f3a8
    s"${self.path.parent.parent.name}-${self.path.parent.name}-${self.path.name}"
  }

  override def domainEventClassTag: ClassTag[DomainEvent] = classTag[DomainEvent]

  override def applyEventPF: ApplyEvent = {
    case (JobSupervisorInitializedEvent(jobId, jobType), EmptyData) => JobData(jobId, jobType, JobStatus.Submitted)
    case (JobExecutionStartedEvent, currentData: JobData)           => currentData.copy(status = JobStatus.Running)
    case (JobExecutionSucceedEvent(result), currentData: JobData)   => currentData.copy(status = JobStatus.Succeeded, result = Some(result))
    case (JobExecutionFailedEvent(result), currentData: JobData)    => currentData.copy(status = JobStatus.Failed, result = Some(result))
    case (JobCancelledEvent, currentData: JobData)                  => currentData.copy(status = JobStatus.Cancelled)
    case (MessagePublishingEvent, currentData: JobData)             => currentData.copy(publishRetriesLeft = messagePublishingRetries)
    case (MessagePublishingRetryEvent, currentData: JobData)        => currentData.copy(publishRetriesLeft = currentData.publishRetriesLeft - 1)
  }

  def jobMessagePublisherWorker: ActorRef = {
    context.child(JobMessagePublisherWorker.Name).getOrElse(context.actorOf(JobMessagePublisherWorker.props(), JobMessagePublisherWorker.Name))
  }

  def jobMasterAppWorker: ActorRef = {
    context.child(JobMasterAppWorker.Name).getOrElse(context.actorOf(JobMasterAppWorker.props(), JobMasterAppWorker.Name))
  }

  startWith(Idle, EmptyData)

  when(Idle) {
    case Event(msg @ JobMessage(JobMessageMeta(jobId, Some(jobType)), SubmitJob(_)), _) =>
      log.info("[ JobId: {} ] - [Submit Job] - SubmitJob msg received at JobSupervisor [{}].", jobId, msg)
      goto(WaitingForSubmissionMsgPublishing) applying JobSupervisorInitializedEvent(jobId, jobType) andThen {
        _ =>
          log.info("[ JobId: {} ] - [Submit Job] - Publishing SubmitJob msg to MasterIn queue", jobId)
          jobMessagePublisherWorker ! PublishToMasterInQueue(msg)
      }
  }

  when(WaitingForSubmissionMsgPublishing, stateTimeout = messagePublishingTimeout) {
    case Event(MessagePublished(JobMessage(_, SubmitJob(_))), job: JobData) => {
      log.info("[ JobId: {} ] - [Submit Job] - Msg publishing succeeded.", job.jobId)
      goto(WaitingForJobMasterAppCreation) andThen (_ => createJobMasterApp(job))
    }

    case Event(Status.Failure(e), job: JobData) => {
      log.error("[ JobId: {} ] - [Submit Job] - Failed to publish msg to MasterIn queue with error: [{}]", job.jobId, e)
      handleFailure(e, job)
    }

    /*
     * It's assumed that at best SubmitJob msg will be in a MasterIn queue when JobSupervisor failed during waiting for ack
     * Otherwise JobMaster starts on Mesos but gets stuck waiting for SumbitJob msg or doesn't start at all
     * As JobMaster doesn't send Heartbeat messages these cases are considered as failure of JobMaster by JobSupervisor
     * (ref. WaitingForExecutionStart state)
     */
    case Event(StateTimeout, job: JobData) => {
      log.warning("[ JobId: {} ] - [Submit Job] - Msg publishing timed out after {}. " +
        "However it's considered as non-fatal error.", job.jobId, messagePublishingTimeout)
      goto(WaitingForJobMasterAppCreation) andThen (_ => createJobMasterApp(job))
    }
  }

  when(WaitingForJobMasterAppCreation, stateTimeout = messagePublishingTimeout) {
    case Event(JobMasterAppCreated(_), job: JobData) => {
      log.info("[ JobId: {} ] - [Create Job Master App] - JobMaster app creation succeeded", job.jobId)
      goto(WaitingForExecutionStart)
    }

    case Event(Status.Failure(e), job: JobData) => {
      log.error("[ JobId: {} ] - [Create Job Master App] - Failed to create JobMaster app with error [{}]", job.jobId, e)
      handleFailure(e, job)
    }

    case Event(StateTimeout, job: JobData) => {
      log.warning("[ JobId: {} ] - [Create Job Master App] - JobMaster app creation timed out after {}. " +
        "However it's necessary to continue and update state to next not to stuck in this one.", job.jobId, messagePublishingTimeout)
      goto(WaitingForExecutionStart)
    }
  }

  when(WaitingForExecutionStart, stateTimeout = jobMasterStartTimeout) {
    case Event(JobMessage(meta, Heartbeat(date: Date, _, _)), job: JobData) => {
      log.info("[ JobId: {} ] - [JobMasterApp start signal ACK] - Start signal from JobMaster received.", job.jobId)
      goto(WaitingForJobStartedMsgPublishing) applying JobExecutionStartedEvent andThen {
        _ =>
          val jobStartedMsg = PublishToStatusQueue(JobMessage(meta, JobStarted(date)))
          log.info("[ JobId: {} ] - [Publish JobStarted msg] - Publishing JobStarted msg to Status queue [{}]", job.jobId, jobStartedMsg)
          jobMessagePublisherWorker ! jobStartedMsg
      }
    }

    case Event(StateTimeout, job: JobData) => {
      log.info("[ JobId: {} ] - [JobMasterApp start signal ACK] - JobMaster app start signal timed out after {}.", job.jobId, jobMasterStartTimeout)
      log.info("[ JobId: {} ] - [Retrieve JobMasterApp status] - Retrieving JobMaster app status", job.jobId)
      jobMasterAppWorker ! GetJobMasterAppStatus(job.jobId)
      stay
    }

    case Event(GetJobMasterAppStatusResult(appStatus), job: JobData) => {
      appStatus match {
        case Some(MarathonClient.AppStatus.Running) => {
          log.error("[ JobId: {} ] - [Retrieve JobMasterApp status] - JobMaster app running but not responding.", job.jobId)
          val result = JobResultFailure(currentDate(), "", "JobMaster app running but not responding")
          goto(WaitingForResultMsgPublishing) applying MessagePublishingEvent applying JobExecutionFailedEvent(result) andThen { _ =>
            publishJobResult(result, job) // TODO: add outputPath value ?
          }
        }
        case Some(_) => {
          log.info("[ JobId: {} ] - [Retrieve JobMasterApp status] - JobMaster app still not running, current status is {}", job.jobId, appStatus)
          stay
        }
        case None => {
          log.error("[ JobId: {} ] - [Retrieve JobMasterApp status] - JobMaster app not found in Mesos", job.jobId)
          val result = JobResultFailure(currentDate(), "", "JobMaster app not found in Mesos")
          goto(WaitingForResultMsgPublishing) applying MessagePublishingEvent applying JobExecutionFailedEvent(result) andThen { _ =>
            publishJobResult(result, job) // TODO: add outputPath value ?
          }
        }
      }
    }

    case Event(Status.Failure(e), job: JobData) => {
      log.error("[ JobId: {} ] - [Retrieve JobMasterApp status] - Failed to get JobMaster app status with error: [{}]", job.jobId, e)
      handleFailure(e, job)
    }
  }

  when(WaitingForJobStartedMsgPublishing, stateTimeout = messagePublishingTimeout) {
    case Event(MessagePublished(JobMessage(_, JobStarted(_))), job: JobData) => {
      log.info("[ JobId: {} ] - [Publish JobStart msg] - Msg publishing succeeded.", job.jobId)
      goto(WaitingForExecutionResult)
    }

    case Event(Status.Failure(e), job: JobData) => {
      log.warning("[ JobId: {} ] - [Publish JobStart msg] - Failed to publish msg with error: [{}] but continue to work", job.jobId, e)
      goto(WaitingForExecutionResult)
    }

    case Event(StateTimeout, job: JobData) => {
      log.warning("[ JobId: {} ] - [Publish JobStart msg] - Msg publishing timed out after {}. " +
        "However it's considered as non-fatal error", job.jobId, messagePublishingTimeout)
      goto(WaitingForExecutionResult)
    }
  }

  when(WaitingForExecutionResult, stateTimeout = jobMasterHeartbeatTimeout) {
    case Event(msg @ JobMessage(_, _: Heartbeat), job: JobData) => {
      // NOTE: for now we're not handling the response after publishing the Heartbeat msg in this state
      // at the JobSupervisor level. Refactor this behaviour if necessary later.
      log.info("[ JobId: {} ] - [Publishing Heartbeat msg] - Publishing Heartbeat msg", job.jobId)
      jobMessagePublisherWorker ! PublishToStatusQueue(msg)
      stay
    }

    case Event(JobMessage(_, result: JobResultSuccess), job: JobData) => {
      log.info("[ JobId: {} ] - Success result msg received: [{}]", job.jobId, result)
      goto(WaitingForResultMsgPublishing) applying MessagePublishingEvent applying JobExecutionSucceedEvent(result) andThen {
        _ => publishJobResult(result, job)
      }
    }

    case Event(JobMessage(_, result: JobResultFailure), job: JobData) => {
      log.info("[ JobId: {} ] - Failure result msg received: [{}]", job.jobId, result)
      goto(WaitingForResultMsgPublishing) applying MessagePublishingEvent applying JobExecutionFailedEvent(result) andThen {
        _ => publishJobResult(result, job)
      }
    }

    case Event(StateTimeout, job: JobData) => {
      log.error("[ JobId: {} ] - No Heartbeat received from JobMaster app after {} - cancelling Job.", job.jobId, jobMasterHeartbeatTimeout)
      val result = JobResultFailure(currentDate(), "", "JobMaster app heartbeat signal timed out.")
      goto(WaitingForResultMsgPublishing) applying MessagePublishingEvent applying JobExecutionFailedEvent(result) andThen {
        _ => publishJobResult(result, job) // TODO add outputPath value
      }
    }
  }

  when(WaitingForResultMsgPublishing, stateTimeout = messagePublishingTimeout) {
    case Event(MessagePublished(JobMessage(_, _: JobResult)), job: JobData) =>
      log.info("[ JobId: {} ] - [Publish JobResult msg] - Msg publishing succeeded.", job.jobId)
      goto(WaitingForResourcesCleanUpMsgPublishing) applying MessagePublishingEvent andThen (_ => askForResourcesCleanup(job))

    case Event(Status.Failure(e), job: JobData) => {
      log.warning("[ JobId: {} ] - [Publish JobResult msg] - Failed to publish msg with error: [{}] but continue to work", job.jobId, e)
      job.result match {
        case Some(result) =>
          handleRetries(job) { job: JobData =>
            goto(WaitingForResultMsgPublishing) applying MessagePublishingRetryEvent andThen (_ => publishJobResult(result, job))
          } { job: JobData =>
            log.warning("[ JobId: {} ] - [Publish JobResult msg] - Not publish result msg: [{}]", job.jobId, job.result)
            goto(WaitingForResourcesCleanUpMsgPublishing) applying MessagePublishingEvent andThen (_ => askForResourcesCleanup(job))
          }
        case None =>
          log.error("[ JobId: {} ] - JobSupervisor state is supposed to contain a result", job.jobId)
          val exception = new IllegalStateException("JobSupervisor state is supposed to contain a result")
          handleFailure(exception, job)
      }
    }

    case Event(StateTimeout, job: JobData) => {
      log.warning("[ JobId: {} ] - [Publish JobResult msg] - Msg publishing timed out after {}. " +
        "However it's considered as non-fatal error", job.jobId, messagePublishingTimeout)
      job.result match {
        case Some(result) =>
          handleRetries(job) { job: JobData =>
            goto(WaitingForResultMsgPublishing) applying MessagePublishingRetryEvent andThen (_ => publishJobResult(result, job))
          } { job: JobData =>
            log.warning("[ JobId: {} ] - [Publish JobResult msg] - Not publish result msg: [{}]", job.jobId, job.result)
            goto(WaitingForResourcesCleanUpMsgPublishing) applying MessagePublishingEvent andThen (_ => askForResourcesCleanup(job))
          }
        case None =>
          log.error("[ JobId: {} ] - JobSupervisor state is supposed to contain a result", job.jobId)
          val exception = new IllegalStateException("JobSupervisor state is supposed to contain a result")
          handleFailure(exception, job)
      }
    }
  }

  /*
   * Try to send CleanUpResources msg until publishing retries will not run out
   * Even if a msg is published two times (at worst) it doesn't break anything (impossible to remove one thing two times)
   */
  when(WaitingForResourcesCleanUpMsgPublishing, stateTimeout = messagePublishingTimeout) {
    case Event(MessagePublished(JobMessage(_, CleanUpResources)), job: JobData) =>
      log.info("[ JobId: {} ] - [Publish CleanUpResources msg] - Msg publishing succeeded.", job.jobId)
      stop andThen (_ =>
        log.info("[ JobId: {} ] - Gracefully stopping JobSupervisor with last state: [{}/{}]", job.jobId, stateName, job))

    case Event(Status.Failure(e), job: JobData) => {
      handleRetries(job) { job: JobData =>
        log.warning("[ JobId: {} ] - [Publish CleanUpResources msg] - Failed to publish msg with error: [{}]. Try to publish one more time", job.jobId, e)
        askForResourcesCleanup(job)
        stay applying MessagePublishingRetryEvent
      }()
    }

    case Event(StateTimeout, job: JobData) => {
      handleRetries(job) { job: JobData =>
        log.warning("[ JobId: {} ] - [Publish CleanUpResources msg] - Msg publishing timed out after {}. " +
          "Try to publish one more time", job.jobId, messagePublishingTimeout)
        askForResourcesCleanup(job)
        stay applying MessagePublishingRetryEvent
      }()
    }
  }

  /**
   * It may be that CancelJob msg has been already published and an acknowledgment hasn't come,
   * but JM has started to handle CancelJob msg and finished hence has sent JobMasterAppReadyForTermination msg
   *
   * It may be that CancelJob msg has been already published and an acknowledgment hasn't come,
   * but JM finished hence has sent JobResult msg so it's supposed to be published for Orion
   *
   * Resources will be cleaned in any case
   *
   * NOTE: There will be no weird behaviour as these cases are conflicting i.e. only one case will happen
   */
  when(WaitingForCancelMsgPublishing, stateTimeout = messagePublishingTimeout) {
    case Event(MessagePublished(JobMessage(_, CancelJob)), job: JobData) => {
      log.info("[ JobId: {} ] - [Publish Cancel msg] - Msg publishing succeeded in status [{}].", job.jobId, job.status)
      goto(WaitingForJobMasterAppTermination) applying JobCancelledEvent
    }

    case Event(Status.Failure(e), job: JobData) => {
      handleRetries(job) { job: JobData =>
        log.warning("[ JobId: {} ] - [Publish Cancel msg] - Failed to publish msg with error: [{}]. Try to publish one more time", job.jobId, e)
        log.info("[ JobId: {} ] - [Publish Cancel msg] - Publishing msg to MasterIn queue in status [{}].", job.jobId, job.status)
        jobMessagePublisherWorker ! PublishToMasterInQueue(JobMessage(JobMessageMeta(job.jobId, Some(job.jobType)), CancelJob))
        stay applying MessagePublishingRetryEvent
      }()
    }

    case Event(StateTimeout, job: JobData) => {
      handleRetries(job) { job: JobData =>
        log.warning("[ JobId: {} ] - [Publish Cancel msg] - Msg publishing timed out after {}. " +
          "Try to publish one more time", job.jobId, messagePublishingTimeout)
        jobMessagePublisherWorker ! PublishToMasterInQueue(JobMessage(JobMessageMeta(job.jobId, Some(job.jobType)), CancelJob))
        stay applying MessagePublishingRetryEvent
      }()
    }

    case Event(JobMessage(_, JobMasterAppReadyForTermination), job: JobData) =>
      log.info("[ JobId: {} ] - JobMaster app ready for termination msg received", job.jobId)
      goto(WaitingForResourcesCleanUpMsgPublishing) applying MessagePublishingEvent andThen (_ => askForResourcesCleanup(job))

    case Event(JobMessage(_, result: JobResultSuccess), job: JobData) => {
      log.info("[ JobId: {} ] - Success result msg received: [{}]", job.jobId, result)
      goto(WaitingForResultMsgPublishing) applying MessagePublishingEvent applying JobExecutionSucceedEvent(result) andThen {
        _ => publishJobResult(result, job)
      }
    }

    case Event(JobMessage(_, result: JobResultFailure), job: JobData) => {
      log.info("[ JobId: {} ] - Failure result msg received: [{}]", job.jobId, result)
      goto(WaitingForResultMsgPublishing) applying MessagePublishingEvent applying JobExecutionFailedEvent(result) andThen {
        _ => publishJobResult(result, job)
      }
    }
  }

  when(WaitingForJobMasterAppTermination) {
    case Event(JobMessage(_, JobMasterAppReadyForTermination), job: JobData) =>
      log.info("[ JobId: {} ] - JobMaster app ready for termination msg received", job.jobId)
      goto(WaitingForResourcesCleanUpMsgPublishing) applying MessagePublishingEvent andThen (_ => askForResourcesCleanup(job))
  }

  whenUnhandled {
    unhandled()
  }

  // To extend the behaviour for testing
  protected def unhandled(): StateFunction = {
    case Event(JobMessage(_, GetJobStatus), job: JobData) => {
      stay replying job.status
    }

    case Event(msg @ JobMessage(_, CancelJob), job: JobData) => {
      log.info("[ JobId: {} ] - CancelJob msg received.", job.jobId)
      goto(WaitingForCancelMsgPublishing) applying MessagePublishingEvent andThen {
        _ =>
          log.info("[ JobId: {} ] - [Publish Cancel msg] - Publishing msg to MasterIn queue in status [{}].", job.jobId, job.status)
          jobMessagePublisherWorker ! PublishToMasterInQueue(msg)
      }
    }

    case Event(e, s) => {
      log.warning("Received unhandled msg [{}] in state [{}/{}]", e, stateName, s)
      stay
    }
  }

  private def createJobMasterApp(job: JobData): Unit = {
    log.info("[ JobId: {} ] - [Create Job Master App] - Creating JobMaster app", job.jobId)
    jobMasterAppWorker ! CreateJobMasterApp(job.jobId)
  }

  private def publishJobResult(result: JobResult, job: JobData): Unit = {
    log.info("[ JobId: {} ] - [Publish JobResult msg] - Publishing msg to Status queue.", job.jobId)
    jobMessagePublisherWorker ! PublishToStatusQueue(JobMessage(JobMessageMeta(job.jobId, Some(job.jobType)), result))
  }

  private def askForResourcesCleanup(job: JobData): Unit = {
    log.info("[ JobId: {} ] - [Publish CleanUpResources msg] - Publishing msg to CleanUp queue.", job.jobId)
    jobMessagePublisherWorker ! PublishToCleanUpResourcesQueue(JobMessage(JobMessageMeta(job.jobId, Some(job.jobType)), CleanUpResources))
  }

  private def handleFailure(e: Throwable, job: JobData): State = {
    // TODO: for now if there's an error of any kind, log and stop self. Refactor
    // if necessary when working on: https://sentrana.atlassian.net/browse/COR-184.
    stop andThen (_ =>
      log.error("[ JobId: {} ] - Unrecoverable error, stopping self with last state [{}/{}]: [{}]", job.jobId, stateName, job, e))
  }

  private def handleRetries(job: JobData)(block: JobData => State)(failureBlock: JobData => State = stopGracefully): State = {
    if (job.publishRetriesLeft > 0) {
      block(job)
    } else {
      log.error("[ JobId: {} ] - Msg has not been published after {} retries.", job.jobId, messagePublishingRetries)
      failureBlock(job)
    }
  }

  private def stopGracefully(job: JobData): State = {
    stop andThen (_ =>
      log.info("[ JobId: {} ] - Gracefully stopping JobSupervisor with last state: [{}/{}]", job.jobId, stateName, job))
  }
}
