package baile.services.dremio.datacontract

import play.api.libs.json.{ Json, OWrites }

case class SQLRequest(sql: String, context: Option[List[String]])

object SQLRequest {

  implicit val SQLRequestWrites: OWrites[SQLRequest] = Json.writes[SQLRequest]

}
