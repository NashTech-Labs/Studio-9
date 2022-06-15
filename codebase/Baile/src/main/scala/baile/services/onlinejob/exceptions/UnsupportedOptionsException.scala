package baile.services.onlinejob.exceptions

import baile.services.onlinejob.OnlineJobCreateOptions

case class UnsupportedOptionsException(options: OnlineJobCreateOptions) extends RuntimeException(
  s"Unsupported online jobs options $options"
)
