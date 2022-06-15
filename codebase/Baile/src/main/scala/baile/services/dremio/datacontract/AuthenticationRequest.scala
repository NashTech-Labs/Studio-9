package baile.services.dremio.datacontract

import play.api.libs.json.{ Json, OWrites }

case class AuthenticationRequest(userName: String, password: String)

object AuthenticationRequest {

  implicit val AuthenticationRequestWrites: OWrites[AuthenticationRequest] = Json.writes[AuthenticationRequest]

}
