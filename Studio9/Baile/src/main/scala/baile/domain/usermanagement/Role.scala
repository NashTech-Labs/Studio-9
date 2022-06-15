package baile.domain.usermanagement

sealed trait Role

object Role {

  case object Admin extends Role

  case object User extends Role

}
