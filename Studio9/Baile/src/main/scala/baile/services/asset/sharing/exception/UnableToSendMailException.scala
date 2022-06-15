package baile.services.asset.sharing.exception

case class UnableToSendMailException(
  email: String,
  cause: Throwable
) extends RuntimeException(s"Unable to send mail for email $email", cause)
