package baile.routes.contract.images

import play.api.libs.json.{ Json, Reads }

case class AlbumSaveRequest(name: String)

object AlbumSaveRequest {
  implicit val AlbumSaveRequestReads: Reads[AlbumSaveRequest] = Json.reads[AlbumSaveRequest]
}
