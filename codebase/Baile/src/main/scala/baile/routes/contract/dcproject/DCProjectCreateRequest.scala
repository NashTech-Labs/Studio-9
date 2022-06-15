package baile.routes.contract.dcproject

import play.api.libs.json.{ Json, Reads }

case class DCProjectCreateRequest(
  name: Option[String],
  description: Option[String]
)

object DCProjectCreateRequest {

  implicit val DCProjectCreateReads: Reads[DCProjectCreateRequest] =
    Json.reads[DCProjectCreateRequest]

}
