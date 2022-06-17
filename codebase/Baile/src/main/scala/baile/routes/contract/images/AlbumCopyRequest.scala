package baile.routes.contract.images

import play.api.libs.json.{ Json, Reads }

case class AlbumCopyRequest(
  pictureIds: Option[Seq[String]],
  name: Option[String],
  description: Option[String],
  copyOnlyLabelledPictures: Option[Boolean],
  inLibrary: Option[Boolean]
)

object AlbumCopyRequest {
  implicit val AlbumCopyRequestReads: Reads[AlbumCopyRequest] = Json.reads[AlbumCopyRequest]
}
