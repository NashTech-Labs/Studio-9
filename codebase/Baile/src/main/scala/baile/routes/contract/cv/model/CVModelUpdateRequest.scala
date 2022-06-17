package baile.routes.contract.cv.model

import play.api.libs.json.{ Json, Reads }

case class CVModelUpdateRequest(
  name: Option[String],
  description: Option[String]
)

object CVModelUpdateRequest {
  implicit val CVModelUpdateRequestReads: Reads[CVModelUpdateRequest] = Json.reads[CVModelUpdateRequest]
}
