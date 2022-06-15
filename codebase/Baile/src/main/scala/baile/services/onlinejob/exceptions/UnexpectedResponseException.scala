package baile.services.onlinejob.exceptions

case class UnexpectedResponseException(message: String) extends RuntimeException(message)
