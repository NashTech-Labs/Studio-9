package baile.routes.contract.images

import baile.routes.contract.images.AlbumTagsSummaryRowResponse.AlbumTagsSummaryRowResponseWrites
import play.api.libs.json.{ JsArray, JsValue, Writes }

case class AlbumTagsSummaryResponse(
  rows: Seq[AlbumTagsSummaryRowResponse]
)

object AlbumTagsSummaryResponse {

  def fromDomain(data: Map[String, Int]): AlbumTagsSummaryResponse = AlbumTagsSummaryResponse(
    rows = data.map {
      case (label, count) => AlbumTagsSummaryRowResponse(
        label = label,
        count = count
      )
    }.toSeq
  )

  implicit val AlbumTagsSummaryResponseWrites: Writes[AlbumTagsSummaryResponse] = new Writes[AlbumTagsSummaryResponse] {
    override def writes(value: AlbumTagsSummaryResponse): JsValue = JsArray(
      value.rows.map(AlbumTagsSummaryRowResponseWrites.writes)
    )
  }
}
