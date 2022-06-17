package cortex.scheduler

import cortex.CortexException

case class SystemCortexException(errorMessage: String, stackTrace: String) extends CortexException(
  s"System exception occurred with message: $errorMessage"
)
