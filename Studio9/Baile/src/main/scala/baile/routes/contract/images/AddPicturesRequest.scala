package baile.routes.contract.images

import baile.domain.images.Picture
import play.api.libs.json.{ Json, Reads }

case class AddPicturesRequest(
  pictureMetaList: Seq[PictureMetaRequest],
  keepExisting: Boolean
) {
  def toDomain(albumId: String): Seq[Picture] =
    pictureMetaList.map(_.toDomain(albumId))
}

object AddPicturesRequest {

  implicit val AddPicturesRequestReads: Reads[AddPicturesRequest] = Json.reads[AddPicturesRequest]

}
