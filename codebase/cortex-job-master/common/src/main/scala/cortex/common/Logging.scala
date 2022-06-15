package cortex.common

import cortex.common.logging.JMLoggerFactory

trait Logging {

  val loggerFactory: JMLoggerFactory

  protected val log: cortex.common.logging.JMLogger = {
    // Ignore trailing $'s in the class names for Scala objects
    val logName = getClass.getName.stripSuffix("$")

    loggerFactory.getLogger(logName)
  }
}
