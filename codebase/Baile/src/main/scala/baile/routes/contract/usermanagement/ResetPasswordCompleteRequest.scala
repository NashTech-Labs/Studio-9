package baile.routes.contract.usermanagement

import play.api.libs.json.{ Json, Reads }

case class ResetPasswordCompleteRequest(
  email: String,
  secretCode: String,
  newPassword: String
)

object ResetPasswordCompleteRequest {
  implicit val ResetPasswordCompleteRequestReads: Reads[ResetPasswordCompleteRequest] =
    Json.reads[ResetPasswordCompleteRequest]
}
