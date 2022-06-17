package cortex.scheduler

import cortex.CortexException

case class UserCortexException(errorCode: String, errorMessage: String, stackTrace: String) extends CortexException(
  s"User exception occurred with code $errorCode and message: $errorMessage"
)
