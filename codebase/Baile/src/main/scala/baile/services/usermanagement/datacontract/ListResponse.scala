package baile.services.usermanagement.datacontract

import play.api.libs.json._

case class ListResponse[T](
  data: Seq[T],
  offset: Long,
  total: Long
)

object ListResponse {

  implicit def ListResponseReads[T: Reads]: Reads[ListResponse[T]] = Json.reads[ListResponse[T]]

}
