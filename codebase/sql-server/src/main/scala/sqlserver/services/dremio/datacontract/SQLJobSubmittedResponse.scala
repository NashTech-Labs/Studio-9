package sqlserver.services.dremio.datacontract

import play.api.libs.json.{ Json, Reads }

case class SQLJobSubmittedResponse(id: String)

object SQLJobSubmittedResponse {

  implicit val SQLJobSubmittedResponseReads: Reads[SQLJobSubmittedResponse] = Json.reads[SQLJobSubmittedResponse]

}
