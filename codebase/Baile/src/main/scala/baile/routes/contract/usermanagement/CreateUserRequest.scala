package baile.routes.contract.usermanagement

import baile.domain.usermanagement.Role
import play.api.libs.json.{ Json, Reads }

case class CreateUserRequest(
  username: String,
  email: String,
  password: String,
  firstName: String,
  lastName: String,
  role: Role
)

object CreateUserRequest {
  implicit val CreateUserRequestReads: Reads[CreateUserRequest] = Json.reads[CreateUserRequest]
}
