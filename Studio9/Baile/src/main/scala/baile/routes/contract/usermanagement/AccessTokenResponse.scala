package baile.routes.contract.usermanagement

import baile.domain.usermanagement.AccessToken
import play.api.libs.json.{ Json, OWrites }

case class AccessTokenResponse(access_token: String, expires_in: Int, token_type: String)

object AccessTokenResponse {
  implicit val AccessTokenResponseWrites: OWrites[AccessTokenResponse] = Json.writes[AccessTokenResponse]

  def fromDomain(model: AccessToken): AccessTokenResponse = {
    AccessTokenResponse(
      access_token = model.token,
      expires_in = model.expiresIn,
      token_type = model.tokenType
    )
  }
}
