package baile.services.cortex.datacontract

import java.time.Instant

import play.api.libs.json.{ Json, Reads }

case class CortexTimeInfoResponse(
  submittedAt: Instant,
  startedAt: Option[Instant],
  completedAt: Option[Instant]
)

object CortexTimeInfoResponse {

  implicit val CortexTimeInfoReads: Reads[CortexTimeInfoResponse] = Json.reads[CortexTimeInfoResponse]

}
