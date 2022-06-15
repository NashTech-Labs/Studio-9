package baile.routes.contract.cv.model

import play.api.libs.json.{ Json, Reads }

case class CVModelSaveRequest(name: String, description: Option[String])

object CVModelSaveRequest {
  implicit val CVModelSaveRequestReads: Reads[CVModelSaveRequest] = Json.reads[CVModelSaveRequest]
}
