package cortex.api.job.message

import java.util.Date

import play.api.libs.json._

import scala.concurrent.duration.FiniteDuration

case class Heartbeat(
    date: Date,
    currentProgress: Double,
    estimatedTimeRemaining: Option[FiniteDuration]
  ) extends JobMessagePayload {
  override val payloadType: String = "HEARTBEAT_PAYLOAD"
}

object Heartbeat {

  implicit val format: OFormat[Heartbeat] = Json.format[Heartbeat]

}
