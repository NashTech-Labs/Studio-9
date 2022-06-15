package baile.routes.contract

import baile.domain.usermanagement.Role
import baile.utils.json.EnumFormatBuilder
import play.api.libs.json.Format

package object usermanagement {

  implicit val RoleFormat: Format[Role] = EnumFormatBuilder.build(
    {
      case "ADMIN" => Role.Admin
      case "USER" => Role.User
    },
    {
      case Role.Admin => "ADMIN"
      case Role.User => "USER"
    },
    "User Role"
  )
}
