package sqlserver.routes.contract.info

import play.api.libs.json.{ Json, OWrites }

case class HealthCheckResponse(isAlive: Boolean)

object HealthCheckResponse {

  implicit val HealthCheckResponseWrites: OWrites[HealthCheckResponse] = Json.writes[HealthCheckResponse]

}
