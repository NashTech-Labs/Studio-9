package baile.services.usermanagement.datacontract

import java.time.ZonedDateTime
import java.util.UUID

import baile.domain.usermanagement.{ RegularUser,  UserStatus, Permission }
import baile.domain.usermanagement.Role
import baile.utils.json.EnumReadsBuilder
import play.api.libs.json._

import scala.collection.immutable.Set

case class UserResponse(
  id: UUID,
  username: String,
  email: String,
  firstName: String,
  lastName: String,
  status: UserStatus,
  fromRootOrg: Boolean,
  created: ZonedDateTime,
  updated: ZonedDateTime,
  permissions: Seq[Permission],
  userGroupIds: Set[String]
) {

  def toDomain(role: Role): RegularUser = RegularUser(
    id = id,
    username = username,
    email = email,
    firstName = firstName,
    lastName = lastName,
    status = status,
    created = created.toInstant,
    updated = updated.toInstant,
    permissions = permissions,
    role = role
  )

}

object UserResponse {
  implicit val UserStatusReads: Reads[UserStatus] = EnumReadsBuilder.build(
    {
      case "ACTIVE" => UserStatus.Active
      case "INACTIVE" => UserStatus.Inactive
      case "DEACTIVATED" => UserStatus.Deactivated
    },
    "user status"
  )

  implicit val PermissionReads: Reads[Permission] = EnumReadsBuilder.build(
    {
      case "SUPERUSER" => Permission.SuperUser
      case _: String =>  Permission.User
    },
    "user permission"
  )

  implicit val UserResponseReads: Reads[UserResponse] = Json.reads[UserResponse]

}
