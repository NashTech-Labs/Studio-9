package baile.services.usermanagement.exceptions

case class UnexpectedResponseException(message: String) extends RuntimeException(message)
