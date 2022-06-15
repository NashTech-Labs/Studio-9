package sqlserver.services.dremio.datacontract

import play.api.libs.json.{ Json, Reads }

case class ColumnTypeResponse(name: ColumnTypeNameResponse)

object ColumnTypeResponse {

  implicit val ColumnTypeResponseReads: Reads[ColumnTypeResponse] = Json.reads[ColumnTypeResponse]

}
