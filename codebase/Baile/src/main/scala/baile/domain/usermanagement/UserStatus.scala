package baile.domain.usermanagement

sealed trait UserStatus

object UserStatus {

  case object Active extends UserStatus

  case object Inactive extends UserStatus

  case object Deactivated extends UserStatus

}
