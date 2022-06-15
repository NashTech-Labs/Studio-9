package baile.routes.contract.onlinejob

import play.api.libs.json.{ Json, Reads }

case class OnlineJobUpdateRequest(
  name: Option[String],
  description: Option[String],
  enabled: Option[Boolean]
)

object OnlineJobUpdateRequest {

  implicit val OnlineJobUpdateRequestReads: Reads[OnlineJobUpdateRequest] = Json.reads[OnlineJobUpdateRequest]

}
