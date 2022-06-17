package baile.routes.contract.dcproject

import play.api.libs.json.{ Json, Reads }

case class DCProjectUpdateRequest(
  name: Option[String],
  description: Option[String]
)

object DCProjectUpdateRequest {

  implicit val ProjectUpdateReads: Reads[DCProjectUpdateRequest] =
    Json.reads[DCProjectUpdateRequest]

}
