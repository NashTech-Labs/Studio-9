package baile.domain.dcproject

sealed trait SessionStatus

object SessionStatus {

  case object Submitted extends SessionStatus

  case object Queued extends SessionStatus

  case object Running extends SessionStatus

  case object Completed extends SessionStatus

  case object Failed extends SessionStatus

}
