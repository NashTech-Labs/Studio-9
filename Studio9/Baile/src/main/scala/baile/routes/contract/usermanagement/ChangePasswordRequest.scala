package baile.routes.contract.usermanagement

import play.api.libs.json.{ Json, Reads }

case class ChangePasswordRequest(
  oldPassword: String,
  newPassword: String
)

object ChangePasswordRequest {
  implicit val ChangePasswordRequestReads: Reads[ChangePasswordRequest] = Json.reads[ChangePasswordRequest]
}
