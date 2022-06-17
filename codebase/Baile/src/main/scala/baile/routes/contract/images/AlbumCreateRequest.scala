package baile.routes.contract.images

import baile.domain.images.AlbumLabelMode
import play.api.libs.json.{ Json, Reads }

case class AlbumCreateRequest(
  name: Option[String],
  labelMode: AlbumLabelMode,
  copyPicturesFrom: Option[Seq[String]],
  copyOnlyLabelledPictures: Option[Boolean],
  description: Option[String],
  inLibrary: Option[Boolean]
)

object AlbumCreateRequest {
  implicit val Format: Reads[AlbumCreateRequest] = Json.reads[AlbumCreateRequest]
}
