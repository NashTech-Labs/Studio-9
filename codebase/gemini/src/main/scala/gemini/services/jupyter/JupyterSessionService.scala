package gemini.services.jupyter

import java.util.UUID

import akka.NotUsed
import akka.actor.{ ActorRef, ActorSystem }
import akka.cluster.pubsub.DistributedPubSubMediator.Subscribe
import akka.pattern.ask
import akka.stream.scaladsl.{ Keep, Sink, Source }
import akka.stream.{ ActorMaterializer, OverflowStrategy }
import akka.util.Timeout
import gemini.domain.jupyter.{ JupyterNodeParams, Session, SessionStatus }
import gemini.domain.remotestorage.TemporaryCredentials
import gemini.services.jupyter.JupyterSessionSupervisor._

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ ExecutionContext, Future }

class JupyterSessionService(
  jupyterSessionSupervisor: ActorRef,
  pubSubMediator: ActorRef,
  actorAskTimeout: FiniteDuration
)(implicit ec: ExecutionContext, actorSystem: ActorSystem) {

  implicit private val timeout: Timeout = Timeout(actorAskTimeout)

  def create(
    temporaryCredentials: TemporaryCredentials,
    folderPath: String,
    userAuthToken: String,
    nodeParams: JupyterNodeParams
  ): Future[Session] = {
    val sessionId = UUID.randomUUID()
    val sessionToken = UUID.randomUUID().toString
    val geminiAuthToken = UUID.randomUUID().toString

    (jupyterSessionSupervisor ? StartSession(
      sessionId = sessionId,
      sessionToken = sessionToken,
      temporaryCredentials = temporaryCredentials,
      folderPath = folderPath,
      userAuthToken = userAuthToken,
      geminiAuthToken = geminiAuthToken,
      nodeParams = nodeParams
    )).mapTo[Session]
  }

  def get(sessionId: UUID): Future[Option[Session]] =
    (jupyterSessionSupervisor ? GetSession(sessionId)).mapTo[Option[Session]]

  def authenticate(sessionId: UUID, token: String): Future[Boolean] =
    (jupyterSessionSupervisor ? SessionAuth(sessionId, token)).mapTo[Boolean]

  def getStatusesSource(sessionId: UUID): Future[Option[Source[SessionStatus, NotUsed]]] =
    get(sessionId).map(_.map { session =>
      val (subscriber, publisher) = Source
        .actorRef[SessionStatus](bufferSize = 0, OverflowStrategy.dropBuffer)
        .toMat(Sink.asPublisher(true))(Keep.both)
        .run()(ActorMaterializer())
      pubSubMediator ! Subscribe(JupyterSessionSupervisor.sessionTopicName(sessionId), subscriber)
      Source
        .fromPublisher(publisher)
        .prepend(Source.single(session.status))
        .takeWhile(!isTerminalStatus(_), inclusive = true)
    })

  def sendHeartbeat(sessionId: UUID): Unit =
    jupyterSessionSupervisor ! SessionHeartbeat(sessionId)

  def stop(sessionId: UUID): Unit =
    jupyterSessionSupervisor ! StopSession(sessionId)

  private def isTerminalStatus(sessionStatus: SessionStatus): Boolean = {
    import SessionStatus._
    sessionStatus match {
      case Submitted | Queued | Running => false
      case Completed | Failed => true
    }
  }

}
