package baile.routes.contract.usermanagement

import play.api.libs.json.{ Json, Reads }

case class ResetPasswordRequest(
  email: String
)

object ResetPasswordRequest {
  implicit val ResetPasswordRequestReads: Reads[ResetPasswordRequest] = Json.reads[ResetPasswordRequest]
}
