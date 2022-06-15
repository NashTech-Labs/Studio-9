package baile.routes.contract.usermanagement

import play.api.libs.json.{ Json, Reads }

case class RemindUsernameRequest(
  email: String
)

object RemindUsernameRequest {
  implicit val RemindUsernameRequestReads: Reads[RemindUsernameRequest] = Json.reads[RemindUsernameRequest]
}
