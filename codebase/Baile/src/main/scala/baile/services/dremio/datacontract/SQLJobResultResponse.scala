package baile.services.dremio.datacontract

import play.api.libs.json._

case class SQLJobResultResponse(
  rowCount: Long,
  rows: List[Map[String, JsValue]]
)

object SQLJobResultResponse {

  implicit def SQLJobResultResponseReads: Reads[SQLJobResultResponse] = Json.reads[SQLJobResultResponse]

}
