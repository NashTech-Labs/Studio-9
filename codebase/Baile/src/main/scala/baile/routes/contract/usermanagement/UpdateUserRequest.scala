package baile.routes.contract.usermanagement

import baile.domain.usermanagement.Role
import play.api.libs.json.{ Json, Reads }

case class UpdateUserRequest(
  username: Option[String],
  email: Option[String],
  password: Option[String],
  firstName: Option[String],
  lastName: Option[String],
  role: Option[Role]
)

object UpdateUserRequest {
  implicit val UpdateUserRequestReads: Reads[UpdateUserRequest] = Json.reads[UpdateUserRequest]
}
