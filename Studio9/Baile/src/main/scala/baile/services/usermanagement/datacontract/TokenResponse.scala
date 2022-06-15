package baile.services.usermanagement.datacontract

import play.api.libs.json.{ Json, Reads }

case class TokenResponse(access_token: String, expires_in: Int, token_type: String)

object TokenResponse {

  implicit val TokenResponseReads: Reads[TokenResponse] = Json.reads[TokenResponse]

}
