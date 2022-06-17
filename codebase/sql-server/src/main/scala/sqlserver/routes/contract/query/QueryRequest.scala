package sqlserver.routes.contract.query

import play.api.libs.json.{ Json, Reads }

case class QueryRequest(
  query: String,
  token: String,
  tables: Map[String, String],
  bindings: Option[Map[String, DBValue]]
)

object QueryRequest {

  implicit val QueryRequestReads: Reads[QueryRequest] = Json.reads[QueryRequest]

}
