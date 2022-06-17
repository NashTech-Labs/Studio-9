package baile.services.cortex.datacontract

import java.util.UUID

import play.api.libs.json._

private[cortex] case class CortexJobCreateRequest(
  id: UUID,
  owner: UUID,
  jobType: String,
  inputPath: String
)

private[cortex] object CortexJobCreateRequest {
  implicit val CortexJobCreateRequestWrites: OWrites[CortexJobCreateRequest] = Json.writes[CortexJobCreateRequest]
}
