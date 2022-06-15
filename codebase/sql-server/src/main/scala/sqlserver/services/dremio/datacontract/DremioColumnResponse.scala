package sqlserver.services.dremio.datacontract

import play.api.libs.json.{ Json, Reads }

case class DremioColumnResponse(name: String, `type`: ColumnTypeResponse)

object DremioColumnResponse {

  implicit val DremioColumnResponseReads: Reads[DremioColumnResponse] = Json.reads[DremioColumnResponse]

}
