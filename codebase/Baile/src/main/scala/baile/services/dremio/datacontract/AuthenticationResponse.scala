package baile.services.dremio.datacontract

import play.api.libs.json.{ Json, Reads }

case class AuthenticationResponse(token: String)

object AuthenticationResponse {

  implicit val AuthenticationResponseReads: Reads[AuthenticationResponse] = Json.reads[AuthenticationResponse]

}
