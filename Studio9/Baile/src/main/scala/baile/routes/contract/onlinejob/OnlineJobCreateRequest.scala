package baile.routes.contract.onlinejob

import baile.domain.asset.AssetReference
import play.api.libs.json.{ Json, Reads }

case class OnlineJobCreateRequest(
  name: Option[String],
  target: AssetReference,
  enabled: Option[Boolean],
  options: OnlineJobCreateOptionsRequest,
  description: Option[String]
)

object OnlineJobCreateRequest {

  import baile.routes.contract.asset.AssetReferenceFormat

  implicit val OnlineJobCreateRequestReads: Reads[OnlineJobCreateRequest] = Json.reads[OnlineJobCreateRequest]

}
