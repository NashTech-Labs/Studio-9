package gemini.services.jupyter

import java.time.Instant
import java.util.UUID

import akka.actor.{ ActorRef, Props }
import akka.cluster.pubsub.DistributedPubSubMediator.Publish
import akka.pattern.after
import akka.persistence.fsm.PersistentFSM
import akka.persistence.fsm.PersistentFSM.FSMState
import gemini.domain.jupyter.{ JupyterNodeParams, Session, SessionStatus }
import gemini.domain.remotestorage.TemporaryCredentials
import gemini.services.jupyter.JupyterSessionSupervisor._
import gemini.utils.ExtendedPersistentFSM

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContextExecutor, Future }
import scala.reflect.{ classTag, ClassTag }
import scala.util.{ Failure, Success }

class JupyterSessionSupervisor(
  settings: Settings,
  pubSubMediator: ActorRef,
  jupyterAppService: JupyterAppService
) extends ExtendedPersistentFSM[State, Data, DomainEvent] {

  implicit private val ec: ExecutionContextExecutor = context.dispatcher

  private val stateTimeout = settings.stateTimeout
  private val waitingForTaskCompletionTime: FiniteDuration = settings.taskKillGracePeriod * 2

  override def applyEventPF: ApplyEvent = {

    def updateSessionStatus(sessionData: SessionData, status: SessionStatus): SessionData =
      sessionData.copy(session = sessionData.session.copy(status = status))

    import DomainEvent._
    {
      case (SessionSupervisionStarted(sessionId, sessionToken, sessionUrl, startedAt, geminiAuthToken), EmptyData) =>
        SessionData(
          session = Session(sessionId, sessionToken, sessionUrl, SessionStatus.Submitted, startedAt),
          geminiAuthToken = geminiAuthToken,
          appId = None,
          errorInfo = None,
          pollingTimeSpent = 0.seconds
        )
      case (AppCreated(appId), data: SessionData) =>
        checkAppStatus(data.session.id, appId)
        updateSessionStatus(data.copy(appId = Some(appId)), SessionStatus.Queued)
      case (SessionStarted, data: SessionData) =>
        updateSessionStatus(data, SessionStatus.Running)
      case (SessionCompleted, data: SessionData) =>
        updateSessionStatus(data, SessionStatus.Completed)
      case (SessionFailed, data: SessionData) =>
        updateSessionStatus(data, SessionStatus.Failed)
      case (ErrorOccured(errorInfo), data: SessionData) =>
        // we keep the first error here
        data.copy(errorInfo = data.errorInfo orElse Some(errorInfo))
      case (AppSuspending, data: SessionData) =>
        checkAppStatus(data.session.id, data.appId.get)
        data
      case (PollingRetried(waitTime), data: SessionData) =>
        data.copy(pollingTimeSpent = data.pollingTimeSpent + waitTime)
      case (PollingCompleted, data: SessionData) =>
        data.copy(pollingTimeSpent = 0.seconds)
    }
  }

  override def domainEventClassTag: ClassTag[DomainEvent] = classTag[DomainEvent]

  override def persistenceId: String = self.path.toStringWithoutAddress

  startWith(Idle, EmptyData)

  when(Idle) {
    case Event(startSession: StartSession, _) =>
      val sessionId = startSession.sessionId
      val scheme = if (settings.useHttpsForUrl) "https" else "http"
      val sessionUrl = s"$scheme://${settings.baseJupyterLabDomain}/$sessionId/"
      goto(WaitingForAppCreate) applying DomainEvent.SessionSupervisionStarted(
        sessionId = sessionId,
        sessionToken = startSession.sessionToken,
        sessionUrl = sessionUrl,
        startedAt = Instant.now(),
        geminiAuthToken = startSession.geminiAuthToken
      ) andThen {
        case data: SessionData =>
          jupyterAppService
            .createApp(
              sessionId = sessionId,
              sessionToken = startSession.sessionToken,
              sessionAccessPath = s"/$sessionId/",
              tempCredentials = startSession.temporaryCredentials,
              folderPath = startSession.folderPath,
              geminiAuthToken = startSession.geminiAuthToken,
              userAuthToken = startSession.userAuthToken,
              nodeParams = startSession.nodeParams
            )
            .foreach(appId => self ! AppCreated(sessionId, appId))

          publishStatus(sessionId, SessionStatus.Submitted)
          sender() ! data.session
        case EmptyData =>
          stop(PersistentFSM.Failure("Persistent empty data after session supervision has started"))
      }
  }

  when(WaitingForAppCreate, stateTimeout = stateTimeout) {
    case Event(AppCreated(sessionId, appId), data: SessionData) if sessionId == data.session.id =>
      goto(WaitingForAppStart) applying DomainEvent.AppCreated(appId) andThen { _ =>
        publishStatus(sessionId, SessionStatus.Queued)
      }
    case Event(StopSession(sessionId), data: SessionData) if sessionId == data.session.id =>
      goto(WaitingForAppCreateForCancel)
    case Event(StateTimeout, data: SessionData) =>
      endWithError(data.session.id, ErrorInfo("INTERNAL-0", "Timeout on waiting for app create"))
  }

  when(WaitingForAppCreateForCancel, stateTimeout = stateTimeout) {
    case Event(AppCreated(sessionId, appId), data: SessionData) if sessionId == data.session.id =>
      suspendApp(data.copy(appId = Some(appId))) applying DomainEvent.AppCreated(appId)
    case Event(StateTimeout, data: SessionData) =>
      endWithError(data.session.id, ErrorInfo("INTERNAL-1", "Timeout on waiting for app create to cancel it"))
  }

  when(WaitingForAppStart) {
    case Event(AppStatus(sessionId, appStatus), data: SessionData) if sessionId == data.session.id =>
      appStatus match {
        case Some(JupyterAppService.AppStatus.Running) =>
          goto(WaitingForSessionStart) applying DomainEvent.PollingCompleted
        case Some(JupyterAppService.AppStatus.Unknown) =>
          handleRetries(data, settings.taskResourcesWaitPeriod) {
            log.info("App is still not running. Making another try to check its status")
            checkAppStatus(sessionId, data.appId.get)
          }
        case None =>
          endWithError(
            data.session.id,
            ErrorInfo("INTERNAL-2", "Unexpectedly not found Jupyter app while waiting for its start")
          )
      }
    case Event(StopSession(sessionId), data: SessionData) if sessionId == data.session.id =>
      suspendApp(data)
  }

  when(WaitingForSessionStart, stateTimeout = stateTimeout) {
    case Event(SessionHeartbeat(sessionId), data: SessionData) if sessionId == data.session.id =>
      goto(SessionRunning) applying DomainEvent.SessionStarted andThen { _ =>
        publishStatus(data.session.id, SessionStatus.Running)
      }
    case Event(StopSession(sessionId), data: SessionData) if sessionId == data.session.id =>
      suspendApp(data)
    case Event(StateTimeout, data: SessionData) =>
      suspendApp(data) applying errorOccured(
        data.session.id,
        ErrorInfo("INTERNAL-3", "Timeout on waiting for session start")
      )
  }

  when(SessionRunning, stateTimeout = stateTimeout) {
    case Event(SessionHeartbeat(sessionId), data: SessionData) if sessionId == data.session.id =>
      stay()
    case Event(StopSession(sessionId), data: SessionData) if sessionId == data.session.id =>
      suspendApp(data)
    case Event(StateTimeout, data: SessionData) =>
      suspendApp(data) applying errorOccured(
        data.session.id,
        ErrorInfo("INTERNAL-4", "Timeout on waiting for heartbeat")
      )
  }

  when(WaitingForAppSuspendingStart, stateTimeout = stateTimeout) {
    case Event(AppSuspendingStarted(sessionId), data: SessionData) if sessionId == data.session.id =>
      goto(WaitingForAppSuspend) applying DomainEvent.AppSuspending
    case Event(AppSuspendingFailed(sessionId, e), data: SessionData) if sessionId == data.session.id =>
      log.error(e, "There was an exception while suspending Jupyter app")
      destroyApp(data) applying errorOccured(
        data.session.id,
        ErrorInfo("INTERNAL-5", s"Unexpected error: ${e.getMessage} while suspending Jupyter app")
      )
    case Event(StateTimeout, data: SessionData) =>
      destroyApp(data) applying errorOccured(
        data.session.id,
        ErrorInfo("INTERNAL-6", "Timeout on waiting for app suspending start")
      )
  }

  when(WaitingForAppSuspend, stateTimeout = stateTimeout) {
    case Event(AppStatus(sessionId, appStatus), data: SessionData) if sessionId == data.session.id =>
      appStatus match {
        case Some(JupyterAppService.AppStatus.Running) =>
          handleRetries(data, waitingForTaskCompletionTime) {
            log.info("App tasks still exist. Making another try to check their status")
            checkAppStatus(sessionId, data.appId.get)
          }
        case Some(_) =>
          destroyApp(data) applying DomainEvent.PollingCompleted
        case None =>
          endWithError(
            data.session.id,
            ErrorInfo("INTERNAL-7", "Unexpectedly not found Jupyter app while waiting for its suspending")
          )
      }
    case Event(StateTimeout, data: SessionData) =>
      endWithError(data.session.id, ErrorInfo("INTERNAL-8", "Timeout on waiting for app suspending"))
  }

  when(WaitingForAppDestroy, stateTimeout = stateTimeout) {
    case Event(AppDestroyed(sessionId), data: SessionData) if sessionId == data.session.id =>
      data.errorInfo match {
        case Some(_) =>
          goto(End) applying DomainEvent.SessionFailed andThen { _ =>
            publishStatus(sessionId, SessionStatus.Failed)
          }
        case None =>
          goto(End) applying DomainEvent.SessionCompleted andThen { _ =>
            publishStatus(data.session.id, SessionStatus.Completed)
          }
      }
    case Event(AppDestroyingFailed(sessionId, e), data: SessionData) if sessionId == data.session.id =>
      log.error(e, "There was an exception while destroying Jupyter app")
      endWithError(
        data.session.id,
        ErrorInfo("INTERNAL-9", s"Unexpected error: ${e.getMessage} while destroying Jupyter app")
      )
    case Event(StateTimeout, data: SessionData) =>
      endWithError(data.session.id, ErrorInfo("INTERNAL-10", "Timeout on waiting for app destroying"))
  }

  when(End, stateTimeout = settings.endTimeout) {
    case Event(StateTimeout, _) =>
      stop()
  }

  whenUnhandled {
    unhandled()
  }

  // To extend the behaviour for testing
  protected[jupyter] def unhandled(): StateFunction = {
    case Event(_: GetSession, EmptyData) =>
      stop() replying None
    case Event(GetSession(sessionId), data: SessionData) if sessionId == data.session.id =>
      stay() replying Some(data.session)
    case Event(_: SessionAuth, EmptyData) =>
      stop() replying false
    case Event(SessionAuth(sessionId, authToken), data: SessionData) if sessionId == data.session.id =>
      stay() replying data.geminiAuthToken == authToken
  }

  private def checkAppStatus(sessionId: UUID, appId: String): Future[Unit] =
    jupyterAppService.getAppStatus(appId).map(self ! AppStatus(sessionId, _))

  private def errorOccured(sessionId: UUID, errorInfo: ErrorInfo): DomainEvent = {
    log.error(
      "[ SessionId: {} ] – Error occured[{}]: {}",
      sessionId,
      errorInfo.errorCode,
      errorInfo.errorMessage
    )

    DomainEvent.ErrorOccured(errorInfo)
  }

  private def endWithError(sessionId: UUID, errorInfo: ErrorInfo): State =
    goto(End) applying errorOccured(sessionId, errorInfo) applying DomainEvent.SessionFailed andThen { _ =>
      publishStatus(sessionId, SessionStatus.Failed)
    }

  private def suspendApp(data: SessionData): State =
    data.appId match {
      case Some(appId) =>
        jupyterAppService.suspendApp(appId).onComplete {
          case Success(_) => self ! AppSuspendingStarted(data.session.id)
          case Failure(e) => self ! AppSuspendingFailed(data.session.id, e)
        }
        goto(WaitingForAppSuspendingStart)
      case None =>
        endWithError(
          data.session.id,
          ErrorInfo("INTERNAL-01", "Could not suspend Jupyter app as app id wasn't defined")
        )
    }

  private def destroyApp(data: SessionData): State =
    data.appId match {
      case Some(appId) =>
        jupyterAppService.destroyApp(appId).onComplete {
          case Success(_) => self ! AppDestroyed(data.session.id)
          case Failure(e) => self ! AppDestroyingFailed(data.session.id, e)
        }
        goto(WaitingForAppDestroy)
      case None =>
        endWithError(
          data.session.id,
          ErrorInfo("INTERNAL-00", "Could not destroy Jupyter app as app id wasn't defined")
        )
    }

  private def publishStatus(sessionId: UUID, status: SessionStatus): Unit = {
    log.info(
      "[ SessionId: {} ] – Status updated: {}",
      sessionId,
      status
    )

    pubSubMediator ! Publish(JupyterSessionSupervisor.sessionTopicName(sessionId), status)
  }

  private def handleRetries(data: SessionData, pollingTime: FiniteDuration)(
    block: => Future[Unit]
  ): State =
    if (data.pollingTimeSpent < pollingTime) {
      after(settings.pollingPeriod, context.system.scheduler) {
        block
      }
      stay() applying DomainEvent.PollingRetried(settings.pollingPeriod)
    } else {
      destroyApp(data) applying errorOccured(
        data.session.id,
        ErrorInfo("INTERNAL-02", s"Polling failed after ${settings.taskResourcesWaitPeriod}.")
      )
    }
}

object JupyterSessionSupervisor {

  val name = "jupyter-session-supervisor"

  case class Settings(
    stateTimeout: FiniteDuration,
    pollingPeriod: FiniteDuration,
    endTimeout: FiniteDuration,
    baseJupyterLabDomain: String,
    useHttpsForUrl: Boolean,
    taskKillGracePeriod: FiniteDuration,
    taskResourcesWaitPeriod: FiniteDuration
  )

  sealed trait Message {
    def sessionId: UUID
  }
  case class StartSession(
    sessionId: UUID,
    sessionToken: String,
    temporaryCredentials: TemporaryCredentials,
    folderPath: String,
    userAuthToken: String,
    geminiAuthToken: String,
    nodeParams: JupyterNodeParams
  ) extends Message
  case class GetSession(sessionId: UUID) extends Message
  case class StopSession(sessionId: UUID) extends Message
  case class SessionHeartbeat(sessionId: UUID) extends Message
  case class SessionAuth(sessionId: UUID, authToken: String) extends Message
  private[jupyter] case class AppCreated(sessionId: UUID, appId: String) extends Message
  private case class AppStatus(sessionId: UUID, appStatus: Option[JupyterAppService.AppStatus]) extends Message
  private case class AppSuspendingStarted(sessionId: UUID) extends Message
  private case class AppSuspendingFailed(sessionId: UUID, exception: Throwable) extends Message
  private case class AppDestroyed(sessionId: UUID) extends Message
  private case class AppDestroyingFailed(sessionId: UUID, exception: Throwable) extends Message

  sealed trait State extends FSMState

  private[jupyter] case object Idle extends State {
    override def identifier: String = "idle"
  }

  private[jupyter] case object WaitingForAppCreate extends State {
    override def identifier: String = "waiting-for-app-create"
  }

  private[jupyter] case object WaitingForAppStart extends State {
    override def identifier: String = "waiting-for-app-start"
  }

  private[jupyter] case object WaitingForSessionStart extends State {
    override def identifier: String = "waiting-for-session-start"
  }

  private[jupyter] case object SessionRunning extends State {
    override def identifier: String = "session-running"
  }

  private[jupyter] case object WaitingForAppSuspendingStart extends State {
    override def identifier: String = "waiting-for-app-suspending-start"
  }

  private[jupyter] case object WaitingForAppCreateForCancel extends State {
    override def identifier: String = "waiting-for-app-create-for-cancel"
  }

  private[jupyter] case object WaitingForAppSuspend extends State {
    override def identifier: String = "waiting-for-app-suspend"
  }

  private[jupyter] case object WaitingForAppDestroy extends State {
    override def identifier: String = "waiting-for-app-destroy"
  }

  private[jupyter] case object End extends State {
    override def identifier: String = "end"
  }

  sealed trait Data
  private case object EmptyData extends Data
  case class SessionData(
    session: Session,
    geminiAuthToken: String,
    appId: Option[String],
    errorInfo: Option[ErrorInfo],
    pollingTimeSpent: FiniteDuration
  ) extends Data

  sealed trait DomainEvent
  private object DomainEvent {
    case class SessionSupervisionStarted(
      sessionId: UUID,
      sessionToken: String,
      sessionUrl: String,
      startedAt: Instant,
      geminiAuthToken: String
    ) extends DomainEvent

    case object SessionStarted extends DomainEvent
    case object SessionFailed extends DomainEvent
    case object SessionCompleted extends DomainEvent

    case class AppCreated(appId: String) extends DomainEvent
    case object AppSuspending extends DomainEvent
    case class ErrorOccured(errorInfo: ErrorInfo) extends DomainEvent

    case class PollingRetried(waitTime: FiniteDuration) extends DomainEvent
    case object PollingCompleted extends DomainEvent
  }

  case class ErrorInfo(errorCode: String, errorMessage: String)

  def props(
    settings: Settings,
    mediator: ActorRef,
    jupyterAppService: JupyterAppService
  ) =
    Props(new JupyterSessionSupervisor(settings, mediator, jupyterAppService))

  def sessionTopicName(sessionId: UUID) = s"sessions-$sessionId"

}
