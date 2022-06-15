package baile.routes.contract.images

import baile.domain.images.AlbumLabelMode
import play.api.libs.json.{ Json, Reads }

case class AlbumUpdateRequest(
  name: Option[String],
  labelMode: Option[AlbumLabelMode],
  description: Option[String]
)

object AlbumUpdateRequest {
  implicit val Format: Reads[AlbumUpdateRequest] = Json.reads[AlbumUpdateRequest]
}
