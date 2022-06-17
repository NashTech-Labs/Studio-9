package baile.routes.contract.asset

import java.util.UUID

import baile.domain.asset.AssetType
import play.api.libs.json._

case class SharedResourceRequest(
  name: Option[String],
  recipientId: Option[UUID],
  recipientEmail: Option[String],
  assetType: AssetType,
  assetId: String
)

object SharedResourceRequest {
  implicit val SharedResourceCreate: Reads[SharedResourceRequest] = Json.reads[SharedResourceRequest]
}
