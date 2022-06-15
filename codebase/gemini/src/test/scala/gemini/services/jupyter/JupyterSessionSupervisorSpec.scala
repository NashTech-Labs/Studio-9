package gemini.services.jupyter

import java.util.UUID

import akka.actor.{ ActorRef, Props }
import akka.cluster.pubsub.DistributedPubSubMediator.Publish
import akka.persistence.fsm.PersistentFSM.{ CurrentState, SubscribeTransitionCallBack, Transition }
import akka.testkit.TestProbe
import gemini.BaseSpec
import gemini.RandomGenerators._
import gemini.domain.jupyter.{ JupyterNodeParams, Session, SessionStatus }
import gemini.domain.remotestorage.S3TemporaryCredentials
import gemini.services.jupyter.JupyterSessionSupervisor._
import mesosphere.marathon.client.MarathonException

import scala.concurrent.Future
import scala.concurrent.duration._

class JupyterSessionSupervisorSpec extends BaseSpec {

  trait Setup { setup =>

    case class GetErrorInfo(sessionId: UUID)

    val stateTimeout: FiniteDuration = 2.seconds

    val settings = Settings(
      stateTimeout = stateTimeout,
      pollingPeriod = 100.millis,
      endTimeout = 10.seconds,
      baseJupyterLabDomain = "deepcortex.ai",
      useHttpsForUrl = false,
      taskKillGracePeriod = 100.millis,
      taskResourcesWaitPeriod = 200.millis
    )
    val jupyterAppService: JupyterAppService = mock[JupyterAppService](withSettings.lenient())
    val pubSubMediator = TestProbe()
    val probe = TestProbe()

    val supervisor: ActorRef =
      system.actorOf(Props(new JupyterSessionSupervisor(settings, pubSubMediator.ref, jupyterAppService) {
        override val persistenceId: String = UUID.randomUUID().toString

        private val sessionDataEvent: StateFunction = {
          case Event(GetErrorInfo(_), data: SessionData) =>
            stay() replying data.errorInfo
        }

        whenUnhandled {
          sessionDataEvent.orElse(unhandled())
        }
      }))

    val sessionId: UUID = UUID.randomUUID()
    val appId = UUID.randomUUID().toString
    val sessionToken = "token"
    val creds = S3TemporaryCredentials("us-east-1", "bucket", randomString(), randomString(), randomString())
    val folderPath = "folder/path"
    val userAuthToken: String = randomString()
    val geminiAuthToken: String = randomString()

    val jupyterNodeParams = JupyterNodeParams(
      numberOfCpus = randomOf(None, Some(randomInt(1, 4))),
      numberOfGpus = randomOf(None, Some(randomInt(1, 4)))
    )

    val watcherProbe = TestProbe()
    supervisor ! SubscribeTransitionCallBack(watcherProbe.ref)
    watcherProbe.expectMsgType[CurrentState[JupyterSessionSupervisor.State]]

    def stateShouldChange(from: JupyterSessionSupervisor.State, to: JupyterSessionSupervisor.State): Unit =
      watcherProbe.expectMsg(Transition(supervisor, from, to, None))

  }

  "JupyterSessionSupervisor" should {

    "make correct transitions and publish completed status in the end for successful series of messages" in new Setup {
      jupyterAppService.createApp(
        sessionId = sessionId,
        sessionToken = sessionToken,
        sessionAccessPath = *,
        tempCredentials = creds,
        folderPath = folderPath,
        userAuthToken = userAuthToken,
        geminiAuthToken = geminiAuthToken,
        nodeParams = jupyterNodeParams
      ) shouldReturn future(appId)
      jupyterAppService
        .getAppStatus(appId)
        .shouldReturn(future(Some(JupyterAppService.AppStatus.Unknown))) // for app starting
        .andThen(future(Some(JupyterAppService.AppStatus.Unknown)))
        .andThen(future(Some(JupyterAppService.AppStatus.Running)))
        .andThen(future(Some(JupyterAppService.AppStatus.Running))) // for app suspending
        .andThen(future(Some(JupyterAppService.AppStatus.Unknown)))
      jupyterAppService.suspendApp(appId) shouldReturn future(())
      jupyterAppService.destroyApp(appId) shouldReturn future(())

      supervisor ! StartSession(
        sessionId = sessionId,
        sessionToken = sessionToken,
        temporaryCredentials = creds,
        folderPath = folderPath,
        userAuthToken = userAuthToken,
        geminiAuthToken = geminiAuthToken,
        nodeParams = jupyterNodeParams
      )
      pubSubMediator.expectMsg(Publish(JupyterSessionSupervisor.sessionTopicName(sessionId), SessionStatus.Submitted))
      stateShouldChange(Idle, WaitingForAppCreate)

      pubSubMediator.expectMsg(Publish(JupyterSessionSupervisor.sessionTopicName(sessionId), SessionStatus.Queued))
      stateShouldChange(WaitingForAppCreate, WaitingForAppStart)

      stateShouldChange(WaitingForAppStart, WaitingForSessionStart)

      supervisor ! SessionHeartbeat(sessionId)
      pubSubMediator.expectMsg(Publish(JupyterSessionSupervisor.sessionTopicName(sessionId), SessionStatus.Running))
      stateShouldChange(WaitingForSessionStart, SessionRunning)

      supervisor ! StopSession(sessionId)
      stateShouldChange(SessionRunning, WaitingForAppSuspendingStart)
      stateShouldChange(WaitingForAppSuspendingStart, WaitingForAppSuspend)
      stateShouldChange(WaitingForAppSuspend, WaitingForAppDestroy)
      pubSubMediator.expectMsg(Publish(JupyterSessionSupervisor.sessionTopicName(sessionId), SessionStatus.Completed))

      stateShouldChange(WaitingForAppDestroy, End)
    }

    "make correct transitions and publish failed status in the end " +
      "when first heartbeat was not received for too long" in new Setup {
      jupyterAppService.createApp(
        sessionId = sessionId,
        sessionToken = sessionToken,
        sessionAccessPath = *,
        tempCredentials = creds,
        folderPath = folderPath,
        userAuthToken = userAuthToken,
        geminiAuthToken = geminiAuthToken,
        nodeParams = jupyterNodeParams
      ) shouldReturn future(appId)
      jupyterAppService.getAppStatus(appId)
        .shouldReturn(future(Some(JupyterAppService.AppStatus.Running)))
        .andThen(future(Some(JupyterAppService.AppStatus.Unknown)))
      jupyterAppService.suspendApp(appId) shouldReturn future(())
      jupyterAppService.destroyApp(appId) shouldReturn future(())

      supervisor ! StartSession(
        sessionId = sessionId,
        sessionToken = sessionToken,
        temporaryCredentials = creds,
        folderPath = folderPath,
        userAuthToken = userAuthToken,
        geminiAuthToken = geminiAuthToken,
        nodeParams = jupyterNodeParams
      )
      pubSubMediator.expectMsg(Publish(JupyterSessionSupervisor.sessionTopicName(sessionId), SessionStatus.Submitted))
      stateShouldChange(Idle, WaitingForAppCreate)

      pubSubMediator.expectMsg(Publish(JupyterSessionSupervisor.sessionTopicName(sessionId), SessionStatus.Queued))
      stateShouldChange(WaitingForAppCreate, WaitingForAppStart)

      stateShouldChange(WaitingForAppStart, WaitingForSessionStart)

      stateShouldChange(WaitingForSessionStart, WaitingForAppSuspendingStart)
      stateShouldChange(WaitingForAppSuspendingStart, WaitingForAppSuspend)
      stateShouldChange(WaitingForAppSuspend, WaitingForAppDestroy)
      pubSubMediator.expectMsg(Publish(JupyterSessionSupervisor.sessionTopicName(sessionId), SessionStatus.Failed))
      stateShouldChange(WaitingForAppDestroy, End)
    }

    "make correct transitions and publish failed status in the end " +
      "when no heartbeats were received for too long" in new Setup {
      jupyterAppService.createApp(
        sessionId = sessionId,
        sessionToken = sessionToken,
        sessionAccessPath = *,
        tempCredentials = creds,
        folderPath = folderPath,
        userAuthToken = userAuthToken,
        geminiAuthToken = geminiAuthToken,
        nodeParams = jupyterNodeParams
      ) shouldReturn future(appId)
      jupyterAppService.getAppStatus(appId)
        .shouldReturn(future(Some(JupyterAppService.AppStatus.Running)))
        .andThen(future(Some(JupyterAppService.AppStatus.Unknown)))
      jupyterAppService.suspendApp(appId) shouldReturn future(())
      jupyterAppService.destroyApp(appId) shouldReturn future(())

      supervisor ! StartSession(
        sessionId = sessionId,
        sessionToken = sessionToken,
        temporaryCredentials = creds,
        folderPath = folderPath,
        userAuthToken = userAuthToken,
        geminiAuthToken = geminiAuthToken,
        nodeParams = jupyterNodeParams
      )
      pubSubMediator.expectMsg(Publish(JupyterSessionSupervisor.sessionTopicName(sessionId), SessionStatus.Submitted))
      stateShouldChange(Idle, WaitingForAppCreate)

      pubSubMediator.expectMsg(Publish(JupyterSessionSupervisor.sessionTopicName(sessionId), SessionStatus.Queued))
      stateShouldChange(WaitingForAppCreate, WaitingForAppStart)

      stateShouldChange(WaitingForAppStart, WaitingForSessionStart)

      supervisor ! SessionHeartbeat(sessionId)
      pubSubMediator.expectMsg(Publish(JupyterSessionSupervisor.sessionTopicName(sessionId), SessionStatus.Running))
      stateShouldChange(WaitingForSessionStart, SessionRunning)

      supervisor ! SessionHeartbeat(sessionId)
      supervisor ! SessionHeartbeat(sessionId)

      stateShouldChange(SessionRunning, WaitingForAppSuspendingStart)
      stateShouldChange(WaitingForAppSuspendingStart, WaitingForAppSuspend)
      stateShouldChange(WaitingForAppSuspend, WaitingForAppDestroy)
      pubSubMediator.expectMsg(Publish(JupyterSessionSupervisor.sessionTopicName(sessionId), SessionStatus.Failed))
      stateShouldChange(WaitingForAppDestroy, End)
    }

    "make correct transitions and publish failed status in the end " +
      "when app has been creating for too long" in new Setup {
      jupyterAppService.createApp(
        sessionId = sessionId,
        sessionToken = sessionToken,
        sessionAccessPath = *,
        tempCredentials = creds,
        folderPath = folderPath,
        userAuthToken = userAuthToken,
        geminiAuthToken = geminiAuthToken,
        nodeParams = jupyterNodeParams
      ) shouldReturn Future.never

      supervisor ! StartSession(
        sessionId = sessionId,
        sessionToken = sessionToken,
        temporaryCredentials = creds,
        folderPath = folderPath,
        userAuthToken = userAuthToken,
        geminiAuthToken = geminiAuthToken,
        nodeParams = jupyterNodeParams
      )
      pubSubMediator.expectMsg(Publish(JupyterSessionSupervisor.sessionTopicName(sessionId), SessionStatus.Submitted))
      stateShouldChange(Idle, WaitingForAppCreate)

      pubSubMediator.expectMsg(Publish(JupyterSessionSupervisor.sessionTopicName(sessionId), SessionStatus.Failed))
      stateShouldChange(WaitingForAppCreate, End)
    }

    "make correct transitions and publish failed status in the end " +
      "if retries are over" in new Setup {
      jupyterAppService.createApp(
        sessionId = sessionId,
        sessionToken = sessionToken,
        sessionAccessPath = *,
        tempCredentials = creds,
        folderPath = folderPath,
        userAuthToken = userAuthToken,
        geminiAuthToken = geminiAuthToken,
        nodeParams = jupyterNodeParams
      ) shouldReturn future(appId)
      jupyterAppService
        .getAppStatus(appId)
        .shouldReturn(future(Some(JupyterAppService.AppStatus.Unknown)))
        .andThen(future(Some(JupyterAppService.AppStatus.Unknown)))
      jupyterAppService.destroyApp(appId) shouldReturn future(())
      supervisor ! StartSession(
        sessionId = sessionId,
        sessionToken = sessionToken,
        temporaryCredentials = creds,
        folderPath = folderPath,
        userAuthToken = userAuthToken,
        geminiAuthToken = geminiAuthToken,
        nodeParams = jupyterNodeParams
      )
      pubSubMediator.expectMsg(Publish(JupyterSessionSupervisor.sessionTopicName(sessionId), SessionStatus.Submitted))
      stateShouldChange(Idle, WaitingForAppCreate)

      pubSubMediator.expectMsg(Publish(JupyterSessionSupervisor.sessionTopicName(sessionId), SessionStatus.Queued))
      stateShouldChange(WaitingForAppCreate, WaitingForAppStart)

      stateShouldChange(WaitingForAppStart, WaitingForAppDestroy)
      pubSubMediator.expectMsg(Publish(JupyterSessionSupervisor.sessionTopicName(sessionId), SessionStatus.Failed))
      stateShouldChange(WaitingForAppDestroy, End)
    }

    "return nothing when asked for session status before starting the session" in new Setup {
      supervisor.tell(GetSession(sessionId), probe.ref)
      probe.expectMsg(None)
    }

    "return status when asked for session status after starting the session" in new Setup {
      jupyterAppService.createApp(
        sessionId = sessionId,
        sessionToken = sessionToken,
        sessionAccessPath = *,
        tempCredentials = creds,
        folderPath = folderPath,
        userAuthToken = userAuthToken,
        geminiAuthToken = geminiAuthToken,
        nodeParams = jupyterNodeParams
      ) shouldReturn future(appId)
      jupyterAppService.getAppStatus(appId) shouldReturn future(Some(JupyterAppService.AppStatus.Running))

      supervisor.tell(
        StartSession(
          sessionId = sessionId,
          sessionToken = sessionToken,
          temporaryCredentials = creds,
          folderPath = folderPath,
          userAuthToken = userAuthToken,
          geminiAuthToken = geminiAuthToken,
          nodeParams = jupyterNodeParams
        ),
        probe.ref
      )
      probe.expectMsgType[Session]
      pubSubMediator.expectMsg(Publish(JupyterSessionSupervisor.sessionTopicName(sessionId), SessionStatus.Submitted))
      stateShouldChange(Idle, WaitingForAppCreate)

      supervisor.tell(GetSession(sessionId), probe.ref)
      probe.expectMsgType[Some[Session]]
    }

    "return false when asked for session auth before starting the session" in new Setup {
      private val authToken = randomString()
      supervisor.tell(SessionAuth(sessionId, authToken), probe.ref)
      probe.expectMsg(false)
    }

    "return true when asked for correct session auth after starting the session" in new Setup {
      jupyterAppService.createApp(
        sessionId = sessionId,
        sessionToken = sessionToken,
        sessionAccessPath = *,
        tempCredentials = creds,
        folderPath = folderPath,
        userAuthToken = userAuthToken,
        geminiAuthToken = geminiAuthToken,
        nodeParams = jupyterNodeParams
      ) shouldReturn future(appId)
      jupyterAppService.getAppStatus(appId) shouldReturn future(Some(JupyterAppService.AppStatus.Running))

      supervisor.tell(
        StartSession(
          sessionId = sessionId,
          sessionToken = sessionToken,
          temporaryCredentials = creds,
          folderPath = folderPath,
          userAuthToken = userAuthToken,
          geminiAuthToken = geminiAuthToken,
          nodeParams = jupyterNodeParams
        ),
        probe.ref
      )
      probe.expectMsgType[Session]
      pubSubMediator.expectMsg(Publish(JupyterSessionSupervisor.sessionTopicName(sessionId), SessionStatus.Submitted))
      stateShouldChange(Idle, WaitingForAppCreate)

      supervisor.tell(SessionAuth(sessionId, geminiAuthToken), probe.ref)
      probe.expectMsg(true)
    }

    "return true when asked for incorrect session auth after starting the session" in new Setup {
      jupyterAppService.createApp(
        sessionId = sessionId,
        sessionToken = sessionToken,
        sessionAccessPath = *,
        tempCredentials = creds,
        folderPath = folderPath,
        userAuthToken = userAuthToken,
        geminiAuthToken = geminiAuthToken,
        nodeParams = jupyterNodeParams
      ) shouldReturn future(appId)
      jupyterAppService.getAppStatus(appId) shouldReturn future(Some(JupyterAppService.AppStatus.Running))

      supervisor.tell(
        StartSession(
          sessionId = sessionId,
          sessionToken = sessionToken,
          temporaryCredentials = creds,
          folderPath = folderPath,
          userAuthToken = userAuthToken,
          geminiAuthToken = geminiAuthToken,
          nodeParams = jupyterNodeParams
        ),
        probe.ref
      )
      probe.expectMsgType[Session]
      pubSubMediator.expectMsg(Publish(JupyterSessionSupervisor.sessionTopicName(sessionId), SessionStatus.Submitted))
      stateShouldChange(Idle, WaitingForAppCreate)

      supervisor.tell(SessionAuth(sessionId, randomString(geminiAuthToken.length+1)), probe.ref)
      probe.expectMsg(false)
    }

    "make correct transitions and publish failed status in the end " +
    "when marathon failed during deleting app" in new Setup {
      jupyterAppService.createApp(
        sessionId = sessionId,
        sessionToken = sessionToken,
        sessionAccessPath = *,
        tempCredentials = creds,
        folderPath = folderPath,
        userAuthToken = userAuthToken,
        geminiAuthToken = geminiAuthToken,
        nodeParams = jupyterNodeParams
      ) shouldReturn future(appId)
      jupyterAppService.getAppStatus(appId)
        .shouldReturn(future(Some(JupyterAppService.AppStatus.Running)))
        .andThen(future(Some(JupyterAppService.AppStatus.Unknown)))
      jupyterAppService.suspendApp(appId) shouldReturn future(())
      jupyterAppService.destroyApp(appId) shouldReturn future(new MarathonException(500, "Any error"))

      supervisor ! StartSession(
        sessionId = sessionId,
        sessionToken = sessionToken,
        temporaryCredentials = creds,
        folderPath = folderPath,
        userAuthToken = userAuthToken,
        geminiAuthToken = geminiAuthToken,
        nodeParams = jupyterNodeParams
      )
      pubSubMediator.expectMsg(Publish(JupyterSessionSupervisor.sessionTopicName(sessionId), SessionStatus.Submitted))
      stateShouldChange(Idle, WaitingForAppCreate)

      pubSubMediator.expectMsg(Publish(JupyterSessionSupervisor.sessionTopicName(sessionId), SessionStatus.Queued))
      stateShouldChange(WaitingForAppCreate, WaitingForAppStart)

      stateShouldChange(WaitingForAppStart, WaitingForSessionStart)

      supervisor ! SessionHeartbeat(sessionId)
      pubSubMediator.expectMsg(Publish(JupyterSessionSupervisor.sessionTopicName(sessionId), SessionStatus.Running))
      stateShouldChange(WaitingForSessionStart, SessionRunning)

      supervisor ! StopSession(sessionId)
      stateShouldChange(SessionRunning, WaitingForAppSuspendingStart)
      stateShouldChange(WaitingForAppSuspendingStart, WaitingForAppSuspend)
      stateShouldChange(WaitingForAppSuspend, WaitingForAppDestroy)
      pubSubMediator.expectMsg(Publish(JupyterSessionSupervisor.sessionTopicName(sessionId), SessionStatus.Failed))

      supervisor.tell(GetErrorInfo(sessionId), probe.ref)
      probe.expectMsg(Some(ErrorInfo("INTERNAL-9", "Unexpected error: Any error (http status: 500) while destroying Jupyter app")))

      stateShouldChange(WaitingForAppDestroy, End)
    }

    "make correct transitions and publish failed status in the end " +
      "when app has been deleting for too long" in new Setup {
      jupyterAppService.createApp(
        sessionId = sessionId,
        sessionToken = sessionToken,
        sessionAccessPath = *,
        tempCredentials = creds,
        folderPath = folderPath,
        userAuthToken = userAuthToken,
        geminiAuthToken = geminiAuthToken,
        nodeParams = jupyterNodeParams
      ) shouldReturn future(appId)
      jupyterAppService.getAppStatus(appId)
        .shouldReturn(future(Some(JupyterAppService.AppStatus.Running)))
        .andThen(future(Some(JupyterAppService.AppStatus.Unknown)))
      jupyterAppService.suspendApp(appId) shouldReturn future(())
      jupyterAppService.destroyApp(appId) shouldReturn Future.never

      supervisor ! StartSession(
        sessionId = sessionId,
        sessionToken = sessionToken,
        temporaryCredentials = creds,
        folderPath = folderPath,
        userAuthToken = userAuthToken,
        geminiAuthToken = geminiAuthToken,
        nodeParams = jupyterNodeParams
      )
      pubSubMediator.expectMsg(Publish(JupyterSessionSupervisor.sessionTopicName(sessionId), SessionStatus.Submitted))
      stateShouldChange(Idle, WaitingForAppCreate)

      pubSubMediator.expectMsg(Publish(JupyterSessionSupervisor.sessionTopicName(sessionId), SessionStatus.Queued))
      stateShouldChange(WaitingForAppCreate, WaitingForAppStart)

      stateShouldChange(WaitingForAppStart, WaitingForSessionStart)

      supervisor ! SessionHeartbeat(sessionId)
      pubSubMediator.expectMsg(Publish(JupyterSessionSupervisor.sessionTopicName(sessionId), SessionStatus.Running))
      stateShouldChange(WaitingForSessionStart, SessionRunning)

      supervisor ! StopSession(sessionId)
      stateShouldChange(SessionRunning, WaitingForAppSuspendingStart)
      stateShouldChange(WaitingForAppSuspendingStart, WaitingForAppSuspend)
      stateShouldChange(WaitingForAppSuspend, WaitingForAppDestroy)
      pubSubMediator.expectMsg(Publish(JupyterSessionSupervisor.sessionTopicName(sessionId), SessionStatus.Failed))

      supervisor.tell(GetErrorInfo(sessionId), probe.ref)
      probe.expectMsg(Some(ErrorInfo("INTERNAL-10", "Timeout on waiting for app destroying")))

      stateShouldChange(WaitingForAppDestroy, End)
    }

    "make correct transitions and publish completed status in the end " +
      "when a session has been stopped during app creation" in new Setup {
      jupyterAppService.createApp(
        sessionId = sessionId,
        sessionToken = sessionToken,
        sessionAccessPath = *,
        tempCredentials = creds,
        folderPath = folderPath,
        userAuthToken = userAuthToken,
        geminiAuthToken = geminiAuthToken,
        nodeParams = jupyterNodeParams
      ) shouldReturn Future.never
      jupyterAppService
        .getAppStatus(appId)
        .shouldReturn(future(Some(JupyterAppService.AppStatus.Unknown)))
      jupyterAppService.suspendApp(appId) shouldReturn future(())
      jupyterAppService.destroyApp(appId) shouldReturn future(())

      supervisor ! StartSession(
        sessionId = sessionId,
        sessionToken = sessionToken,
        temporaryCredentials = creds,
        folderPath = folderPath,
        userAuthToken = userAuthToken,
        geminiAuthToken = geminiAuthToken,
        nodeParams = jupyterNodeParams
      )
      pubSubMediator.expectMsg(Publish(JupyterSessionSupervisor.sessionTopicName(sessionId), SessionStatus.Submitted))
      stateShouldChange(Idle, WaitingForAppCreate)

      supervisor ! StopSession(sessionId)
      stateShouldChange(WaitingForAppCreate, WaitingForAppCreateForCancel)

      supervisor ! AppCreated(sessionId, appId)

      stateShouldChange(WaitingForAppCreateForCancel, WaitingForAppSuspendingStart)
      stateShouldChange(WaitingForAppSuspendingStart, WaitingForAppSuspend)
      stateShouldChange(WaitingForAppSuspend, WaitingForAppDestroy)
      pubSubMediator.expectMsg(Publish(JupyterSessionSupervisor.sessionTopicName(sessionId), SessionStatus.Completed))

      stateShouldChange(WaitingForAppDestroy, End)
    }

    "make correct transitions and publish failed status in the end " +
      "when app has been creating for too long after stop session" in new Setup {
      jupyterAppService.createApp(
        sessionId = sessionId,
        sessionToken = sessionToken,
        sessionAccessPath = *,
        tempCredentials = creds,
        folderPath = folderPath,
        userAuthToken = userAuthToken,
        geminiAuthToken = geminiAuthToken,
        nodeParams = jupyterNodeParams
      ) shouldReturn Future.never

      supervisor ! StartSession(
        sessionId = sessionId,
        sessionToken = sessionToken,
        temporaryCredentials = creds,
        folderPath = folderPath,
        userAuthToken = userAuthToken,
        geminiAuthToken = geminiAuthToken,
        nodeParams = jupyterNodeParams
      )
      pubSubMediator.expectMsg(Publish(JupyterSessionSupervisor.sessionTopicName(sessionId), SessionStatus.Submitted))
      stateShouldChange(Idle, WaitingForAppCreate)

      supervisor ! StopSession(sessionId)
      stateShouldChange(WaitingForAppCreate, WaitingForAppCreateForCancel)

      pubSubMediator.expectMsg(Publish(JupyterSessionSupervisor.sessionTopicName(sessionId), SessionStatus.Failed))
      stateShouldChange(WaitingForAppCreateForCancel, End)
    }

    "make correct transitions and publish completed status in the end " +
      "when a session has been stopped during app start" in new Setup {
      jupyterAppService.createApp(
        sessionId = sessionId,
        sessionToken = sessionToken,
        sessionAccessPath = *,
        tempCredentials = creds,
        folderPath = folderPath,
        userAuthToken = userAuthToken,
        geminiAuthToken = geminiAuthToken,
        nodeParams = jupyterNodeParams
      ) shouldReturn future(appId)
      jupyterAppService
        .getAppStatus(appId)
        .shouldReturn(future(Some(JupyterAppService.AppStatus.Unknown)))
      jupyterAppService.suspendApp(appId) shouldReturn future(())
      jupyterAppService.destroyApp(appId) shouldReturn future(())

      supervisor ! StartSession(
        sessionId = sessionId,
        sessionToken = sessionToken,
        temporaryCredentials = creds,
        folderPath = folderPath,
        userAuthToken = userAuthToken,
        geminiAuthToken = geminiAuthToken,
        nodeParams = jupyterNodeParams
      )
      pubSubMediator.expectMsg(Publish(JupyterSessionSupervisor.sessionTopicName(sessionId), SessionStatus.Submitted))
      stateShouldChange(Idle, WaitingForAppCreate)

      pubSubMediator.expectMsg(Publish(JupyterSessionSupervisor.sessionTopicName(sessionId), SessionStatus.Queued))
      stateShouldChange(WaitingForAppCreate, WaitingForAppStart)

      supervisor ! StopSession(sessionId)
      stateShouldChange(WaitingForAppStart, WaitingForAppSuspendingStart)
      stateShouldChange(WaitingForAppSuspendingStart, WaitingForAppSuspend)
      stateShouldChange(WaitingForAppSuspend, WaitingForAppDestroy)
      pubSubMediator.expectMsg(Publish(JupyterSessionSupervisor.sessionTopicName(sessionId), SessionStatus.Completed))

      stateShouldChange(WaitingForAppDestroy, End)
    }

    "make correct transitions and publish completed status in the end " +
      "when a session has been stopped during session start" in new Setup {
      jupyterAppService.createApp(
        sessionId = sessionId,
        sessionToken = sessionToken,
        sessionAccessPath = *,
        tempCredentials = creds,
        folderPath = folderPath,
        userAuthToken = userAuthToken,
        geminiAuthToken = geminiAuthToken,
        nodeParams = jupyterNodeParams
      ) shouldReturn future(appId)
      jupyterAppService
        .getAppStatus(appId)
        .shouldReturn(future(Some(JupyterAppService.AppStatus.Running))) // for app starting
        .andThen(future(Some(JupyterAppService.AppStatus.Unknown))) // for app suspending
      jupyterAppService.suspendApp(appId) shouldReturn future(())
      jupyterAppService.destroyApp(appId) shouldReturn future(())

      supervisor ! StartSession(
        sessionId = sessionId,
        sessionToken = sessionToken,
        temporaryCredentials = creds,
        folderPath = folderPath,
        userAuthToken = userAuthToken,
        geminiAuthToken = geminiAuthToken,
        nodeParams = jupyterNodeParams
      )
      pubSubMediator.expectMsg(Publish(JupyterSessionSupervisor.sessionTopicName(sessionId), SessionStatus.Submitted))
      stateShouldChange(Idle, WaitingForAppCreate)

      pubSubMediator.expectMsg(Publish(JupyterSessionSupervisor.sessionTopicName(sessionId), SessionStatus.Queued))
      stateShouldChange(WaitingForAppCreate, WaitingForAppStart)

      stateShouldChange(WaitingForAppStart, WaitingForSessionStart)

      supervisor ! StopSession(sessionId)
      stateShouldChange(WaitingForSessionStart, WaitingForAppSuspendingStart)
      stateShouldChange(WaitingForAppSuspendingStart, WaitingForAppSuspend)
      stateShouldChange(WaitingForAppSuspend, WaitingForAppDestroy)
      pubSubMediator.expectMsg(Publish(JupyterSessionSupervisor.sessionTopicName(sessionId), SessionStatus.Completed))

      stateShouldChange(WaitingForAppDestroy, End)
    }
  }

}
