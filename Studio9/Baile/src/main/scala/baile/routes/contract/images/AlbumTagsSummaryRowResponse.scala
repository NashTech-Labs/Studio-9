package baile.routes.contract.images

import play.api.libs.json.{ Json, OWrites }

case class AlbumTagsSummaryRowResponse(
  label: String,
  count: Int
)

object AlbumTagsSummaryRowResponse {
  implicit val AlbumTagsSummaryRowResponseWrites: OWrites[AlbumTagsSummaryRowResponse] =
    Json.writes[AlbumTagsSummaryRowResponse]
}
