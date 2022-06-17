package sqlserver.services.usermanagement.datacontract

import java.util.UUID

import play.api.libs.json._

case class UserResponse(
  id: UUID,
  username: String,
  email: String,
  firstName: String,
  lastName: String
)

object UserResponse {
  implicit val UserResponseReads: Reads[UserResponse] = Json.reads[UserResponse]

}
