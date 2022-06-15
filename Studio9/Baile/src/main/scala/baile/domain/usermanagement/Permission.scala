package baile.domain.usermanagement

sealed trait Permission

object Permission {

  case object SuperUser extends Permission

  case object User extends Permission

}
