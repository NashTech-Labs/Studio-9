package baile.services.usermanagement.exceptions

import baile.services.usermanagement.UmService.GetUserError

case class GetRegisteredUserFailedException(error: GetUserError) extends RuntimeException(
  s"Could not get user right after registration went successful with error $error"
)
