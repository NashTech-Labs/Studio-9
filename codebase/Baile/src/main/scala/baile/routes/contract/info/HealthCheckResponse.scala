package baile.routes.contract.info

import play.api.libs.json.{ Json, OWrites }

case class HealthCheckResponse(ok: Boolean)

object HealthCheckResponse {

  implicit val HealthCheckResponseWrites: OWrites[HealthCheckResponse] = Json.writes[HealthCheckResponse]

}
