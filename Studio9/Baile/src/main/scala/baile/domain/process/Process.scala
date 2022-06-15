package baile.domain.process

import java.time.Instant
import java.util.UUID

import baile.domain.asset.AssetType
import play.api.libs.json.JsObject

import scala.concurrent.duration.FiniteDuration

case class Process(
  targetId: String,
  targetType: AssetType,
  ownerId: UUID,
  authToken: Option[String],
  jobId: UUID,
  status: ProcessStatus,
  progress: Option[Double], // It should be in range: 0 to 1
  estimatedTimeRemaining: Option[FiniteDuration],
  created: Instant,
  started: Option[Instant],
  completed: Option[Instant],
  errorCauseMessage: Option[String],
  errorDetails: Option[String],
  onComplete: ResultHandlerMeta,
  auxiliaryOnComplete: Seq[ResultHandlerMeta]
)

case class ResultHandlerMeta(
  handlerClassName: String,
  meta: JsObject
)
