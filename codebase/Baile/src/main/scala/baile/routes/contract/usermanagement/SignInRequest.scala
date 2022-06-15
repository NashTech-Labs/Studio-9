package baile.routes.contract.usermanagement

import play.api.libs.json.{ Json, Reads }

case class SignInRequest(
  username: Option[String],
  email: Option[String],
  password: String
)

object SignInRequest {
  implicit val SignInRequestReads: Reads[SignInRequest] = Json.reads[SignInRequest]
}
