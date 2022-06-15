package baile.routes.contract.images

import baile.domain.images.Video
import play.api.libs.json.{ Json, OWrites }

case class AlbumVideoResponse (
  filepath: String,
  filename: String,
  filesize: Long
)

object AlbumVideoResponse {
  implicit val AlbumVideoResponseWrites: OWrites[AlbumVideoResponse] = Json.writes[AlbumVideoResponse]

  def fromDomain(in: Video): AlbumVideoResponse =
    AlbumVideoResponse(
      filepath = in.filePath,
      filename = in.fileName,
      filesize = in.fileSize
    )
}
