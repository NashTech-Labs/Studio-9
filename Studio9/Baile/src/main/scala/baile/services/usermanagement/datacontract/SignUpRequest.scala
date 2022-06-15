package baile.services.usermanagement.datacontract

import play.api.libs.json.{ Json, OWrites }

case class SignUpRequest(
  username: String,
  email: String,
  password: String,
  firstName: String,
  lastName: String,
  requireEmailConfirmation: Boolean
)

object SignUpRequest {
  implicit val SignUpRequestWrites: OWrites[SignUpRequest] = Json.writes[SignUpRequest]
}

