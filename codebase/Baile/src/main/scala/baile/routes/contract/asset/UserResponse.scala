package baile.routes.contract.asset

import java.util.UUID

import baile.domain.usermanagement.User
import play.api.libs.json.{ Json, OWrites }

case class UserResponse(
  id: UUID,
  email: String,
  firstName: String,
  lastName: String
)

object UserResponse {
  implicit val UserResponseWrites: OWrites[UserResponse] = Json.writes[UserResponse]

  def fromDomain(model: User): UserResponse = {
    UserResponse(
      id = model.id,
      email = model.email,
      firstName = model.firstName,
      lastName = model.lastName
    )
  }

}
