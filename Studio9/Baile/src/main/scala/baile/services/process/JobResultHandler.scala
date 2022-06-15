package baile.services.process

import java.util.UUID

import akka.actor.Actor
import akka.pattern.pipe
import baile.domain.job.CortexJobTerminalStatus
import baile.services.process.JobResultHandler.{ HandleException, HandleJobResult }
import baile.utils.TryExtensions._
import play.api.libs.json.{ JsError, JsObject, JsSuccess, Reads }

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

abstract class JobResultHandler[M] extends Actor {

  protected val metaReads: Reads[M]

  protected implicit val ec: ExecutionContext = context.dispatcher

  override final def receive: Receive = {
    case HandleJobResult(jobId, lastStatus, meta) => handleResult(jobId, lastStatus, meta) pipeTo sender
    case HandleException(meta) => handleException(meta) pipeTo sender
  }

  protected def handleResult(
    jobId: UUID,
    lastStatus: CortexJobTerminalStatus,
    meta: M
  )(implicit ec: ExecutionContext): Future[Unit]

  protected def handleException(meta: M): Future[Unit]

  private def handleResult(
    jobId: UUID,
    lastStatus: CortexJobTerminalStatus,
    rawMeta: JsObject
  ): Future[Unit] =
    withMeta(rawMeta)(handleResult(jobId, lastStatus, _))

  private def handleException(rawMeta: JsObject): Future[Unit] =
    withMeta(rawMeta)(handleException)

  protected def mapErrorCause(
    meta: M,
    code: String,
    message: String
  )(implicit ec: ExecutionContext): Future[Option[String]] = Future.successful(None)

  private def withMeta(rawMeta: JsObject)(f: M => Future[Unit]): Future[Unit] =
    for {
      meta <- Try {
        metaReads.reads(rawMeta) match {
          case JsSuccess(meta, _) => meta
          case JsError(errors) => throw MetaParsingException(rawMeta, errors)
        }
      }.toFuture
      result <- f(meta)
    } yield result

}

object JobResultHandler {
  sealed trait HandlerMessage
  case class HandleJobResult(jobId: UUID, lastStatus: CortexJobTerminalStatus, rawMeta: JsObject) extends HandlerMessage
  case class HandleException(rawMeta: JsObject) extends HandlerMessage
}
