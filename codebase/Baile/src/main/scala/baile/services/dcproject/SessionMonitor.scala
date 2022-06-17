package baile.services.dcproject

import akka.actor.{ Actor, ActorLogging, Props }
import akka.event.LoggingAdapter
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import baile.dao.dcproject.{ DCProjectDao, SessionDao }
import baile.daocommons.WithId
import baile.daocommons.filters.TrueFilter
import baile.domain.dcproject.{ DCProject, DCProjectStatus, Session }
import baile.services.common.EntityUpdateFailedException
import baile.services.dcproject.SessionMonitor.SubscribeSession
import baile.services.gemini.GeminiService
import baile.utils.ThrowableExtensions._
import cortex.api.gemini.SessionStatus.{ Completed, Failed }

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ Await, ExecutionContext, Future }
import scala.util.{ Failure, Success }

class SessionMonitor private[dcproject](
  val dcProjectDao: DCProjectDao,
  val sessionDao: SessionDao,
  val geminiService: GeminiService,
  private val startupRequestTimeout: FiniteDuration
)(implicit val materializer: Materializer) extends Actor with ActorLogging {

  implicit val ec: ExecutionContext = context.dispatcher
  implicit val logger: LoggingAdapter = log

  override def preStart(): Unit = {
    super.preStart()
    logger.info("Loading current session from storage")
    val sessions = Await.result(sessionDao.listAll(TrueFilter), startupRequestTimeout)
    sessions foreach subscribeSession
  }

  override def receive: Receive = {
    case SubscribeSession(session) => sender() ! subscribeSession(session)
  }

  private def subscribeSession(session: WithId[Session]): Unit = {
    val sourcesF = geminiService.getSessionStatusesSource(session.entity.geminiSessionId)
    sourcesF.onComplete {
      case Success(source) =>
        val lastStatusResult = source.runWith(Sink.last)
        lastStatusResult.onComplete {
          case Success(Completed | Failed) =>
            val result = for {
              _ <- sessionDao.delete(session.id)
              _ <- updateDCProjectStatus(session.entity.dcProjectId, DCProjectStatus.Idle)
            } yield ()
            result.onComplete {
              case Success(_) =>
                logger.debug("Deleted session [{}]", session.id)
              case Failure(t) =>
                logger.error(
                  "Could not delete finished session [{}]. Error: [{}]",
                  session.id,
                  t.printInfo
                )
            }
          case Success(unexpected) =>
            logger.error("Unexpected last session status from gemini source [{}]", unexpected)
          case Failure(t) =>
            logger.warning("Error on retrieving last status from source: [{}]. Retrying...", t.printInfo)
            subscribeSession(session)
        }
      case Failure(t) =>
        logger.error("Could not get sessions status source for session [{}]. Error: [{}]", session.id, t.printInfo)
    }

  }

  private def updateDCProjectStatus(projectId: String, status: DCProjectStatus): Future[WithId[DCProject]] = {
    dcProjectDao.update(projectId, _.copy(status = status)).map(
      _.getOrElse(throw EntityUpdateFailedException(projectId, classOf[DCProject]))
    )
  }

}

object SessionMonitor {

  def props(
    dcProjectDao: DCProjectDao,
    sessionDao: SessionDao,
    geminiService: GeminiService,
    startupRequestTimeout: FiniteDuration
  )(implicit ec: ExecutionContext, materializer: Materializer): Props =
    Props(new SessionMonitor(
      dcProjectDao,
      sessionDao,
      geminiService,
      startupRequestTimeout
    ))

  private[dcproject] case class SubscribeSession(session: WithId[Session])

}
