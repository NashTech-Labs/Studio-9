package baile.routes.contract.usermanagement

import java.time.Instant
import java.util.UUID

import baile.domain.usermanagement.{ RegularUser, Role, UserStatus }
import baile.utils.json.EnumWritesBuilder
import play.api.libs.json.{ Json, OWrites, Writes }

case class UserResponse(
  id: UUID,
  username: String,
  email: String,
  firstName: String,
  lastName: String,
  status: UserStatus,
  created: Instant,
  updated: Instant,
  role: Role
)

object UserResponse {

  implicit val UserStatusWrites: Writes[UserStatus] = EnumWritesBuilder.build {
    case UserStatus.Inactive => "INACTIVE"
    case UserStatus.Active => "ACTIVE"
    case UserStatus.Deactivated => "DEACTIVATED"
  }

  implicit val UserResponseWrites: OWrites[UserResponse] = Json.writes[UserResponse]

  def fromDomain(user: RegularUser): UserResponse = {
    UserResponse(
      id = user.id,
      email = user.email,
      firstName = user.firstName,
      lastName = user.lastName,
      username = user.username,
      status = user.status,
      created = user.created,
      updated = user.updated,
      role = user.role
    )
  }
}
