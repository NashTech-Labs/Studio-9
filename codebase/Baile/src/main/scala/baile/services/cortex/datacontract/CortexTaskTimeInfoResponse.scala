package baile.services.cortex.datacontract

import play.api.libs.json.{ Json, Reads }

case class CortexTaskTimeInfoResponse(
  taskName: String,
  timeInfo: CortexTimeInfoResponse
)

object CortexTaskTimeInfoResponse {

  implicit val CortexTaskTimeInfoReads: Reads[CortexTaskTimeInfoResponse] = Json.reads[CortexTaskTimeInfoResponse]

}
