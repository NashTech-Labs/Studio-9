package gemini.services.jupyter

import java.time.Instant
import java.util.UUID

import akka.cluster.pubsub.DistributedPubSubMediator.Subscribe
import akka.stream.scaladsl.Sink
import akka.testkit.TestProbe
import gemini.BaseSpec
import gemini.RandomGenerators._
import gemini.domain.jupyter.{ JupyterNodeParams, Session, SessionStatus }
import gemini.domain.remotestorage.S3TemporaryCredentials
import gemini.services.jupyter.JupyterSessionSupervisor._

class JupyterSessionServiceSpec extends BaseSpec {

  trait Setup {
    val jupyterSessionSupervisor = TestProbe()
    val pubSubMediator = TestProbe()

    val service = new JupyterSessionService(
      jupyterSessionSupervisor.ref,
      pubSubMediator.ref,
      patienceConfig.timeout
    )
    val session = Session(
      id = UUID.randomUUID(),
      token = "token",
      url = "http://deepcortex.ai/42/",
      status = SessionStatus.Submitted,
      startedAt = Instant.now
    )
    val creds = S3TemporaryCredentials("us-east-1", "bucket", randomString(), randomString(), randomString())
    val folderPath = "folder/path"

    val userAuthToken: String = randomString()

    val jupyterNodeParams = JupyterNodeParams(
      numberOfCpus = randomOf(None, Some(randomInt(1, 4))),
      numberOfGpus = randomOf(None, Some(randomInt(1, 4)))
    )
  }

  "JupyterSessionService#create" should {
    "create session" in new Setup {
      val result = service.create(
        temporaryCredentials = creds,
        folderPath = folderPath,
        userAuthToken = userAuthToken,
        nodeParams = jupyterNodeParams
      )
      jupyterSessionSupervisor.expectMsgType[StartSession]
      jupyterSessionSupervisor.reply(session)
      whenReady(result)(_ shouldBe session)
    }
  }

  "JupyterSessionService#get" should {

    "return session" in new Setup {
      whenReady {
        val result = service.get(session.id)
        jupyterSessionSupervisor.expectMsg(GetSession(session.id))
        jupyterSessionSupervisor.reply(Some(session))
        result
      } { _ shouldBe Some(session) }
    }

    "return nothing when there is no session" in new Setup {
      whenReady {
        val result = service.get(session.id)
        jupyterSessionSupervisor.expectMsg(GetSession(session.id))
        jupyterSessionSupervisor.reply(None)
        result
      } { _ shouldBe None }
    }

  }

  "JupyterSessionService#authenticate" should {

    "return true when supervisor returns true" in new Setup {
      whenReady {
        val authToken = randomString()

        val result = service.authenticate(session.id, authToken)
        jupyterSessionSupervisor.expectMsg(SessionAuth(session.id, authToken))
        jupyterSessionSupervisor.reply(true)
        result
      } { _ shouldBe true }
    }

    "return false when supervisor returns false" in new Setup {
      whenReady {
        val authToken = randomString()

        val result = service.authenticate(session.id, authToken)
        jupyterSessionSupervisor.expectMsg(SessionAuth(session.id, authToken))
        jupyterSessionSupervisor.reply(false)
        result
      } { _ shouldBe false }
    }

  }

  "JupyterSessionService#getStatusesSource" should {

    "return source of session statuses" in new Setup {
      import SessionStatus._
      whenReady {
        val methodResult = service.getStatusesSource(session.id)
        jupyterSessionSupervisor.expectMsg(GetSession(session.id))
        jupyterSessionSupervisor.reply(Some(session))
        val subscriber = pubSubMediator.expectMsgPF() {
          case Subscribe(topic, None, ref) if topic == JupyterSessionSupervisor.sessionTopicName(session.id) => ref
        }
        subscriber ! Queued
        subscriber ! Running
        subscriber ! Completed
        for {
          source <- methodResult.map(_.get)
          result <- source.runWith(Sink.seq)
        } yield result
      } { _ shouldBe Seq(Submitted, Queued, Running, Completed) }
    }

    "return nothing when there is no session" in new Setup {
      whenReady {
        val result = service.getStatusesSource(session.id)
        jupyterSessionSupervisor.expectMsg(GetSession(session.id))
        jupyterSessionSupervisor.reply(None)
        result
      } { _ shouldBe None }
    }

  }

  "JupyterSessionService#sendHeartbeat" should {
    "send a heartbeat" in new Setup {
      service.sendHeartbeat(session.id)
      jupyterSessionSupervisor.expectMsg(SessionHeartbeat(session.id))
    }
  }

  "JupyterSessionService#stop" should {
    "stop session" in new Setup {
      service.stop(session.id)
      jupyterSessionSupervisor.expectMsg(StopSession(session.id))
    }
  }

}
