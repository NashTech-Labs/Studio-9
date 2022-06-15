package baile.routes.contract.usermanagement

import play.api.libs.json.{ Json, Reads }

case class SignUpRequest(
  username: String,
  email: String,
  password: String,
  firstName: String,
  lastName: String
)

object SignUpRequest {
  implicit val SignUpRequestReads: Reads[SignUpRequest] = Json.reads[SignUpRequest]
}
