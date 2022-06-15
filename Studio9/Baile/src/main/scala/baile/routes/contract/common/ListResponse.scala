package baile.routes.contract.common

import play.api.libs.json._

case class ListResponse[T] (
  data: Seq[T],
  count: Long
)

object ListResponse {
  implicit def ListResponseWrites[T: Writes]: OWrites[ListResponse[T]] = Json.writes[ListResponse[T]]
}
