package baile.services.usermanagement.datacontract

import play.api.libs.json.{ Json, OWrites }

case class CreateUserRequest(
  username: String,
  email: String,
  password: String,
  firstName: String,
  lastName: String,
  groupIds: Set[String],
  requireEmailConfirmation: Boolean
)

object CreateUserRequest {
  implicit val CreateUserRequestWrites: OWrites[CreateUserRequest] = Json.writes[CreateUserRequest]
}
