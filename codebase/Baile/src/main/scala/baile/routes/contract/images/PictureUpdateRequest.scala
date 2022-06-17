package baile.routes.contract.images

import baile.domain.images.PictureTag
import play.api.libs.json.{ Json, Reads }

case class PictureUpdateRequest(caption: Option[String], tags: Seq[PictureTag])

object PictureUpdateRequest {
  implicit val PictureUpdateReads: Reads[PictureUpdateRequest] = Json.reads[PictureUpdateRequest]
}
