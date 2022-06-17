package cortex.common.logging

import org.slf4j.LoggerFactory

class JMLogger private[logging] (loggerName: String, jobId: String) {

  private val logger = LoggerFactory.getLogger(loggerName)

  protected def jmMessage(msg: String): String = {
    s"[ JobId: ${jobId.toString} ] - [JobMaster] - [$msg]"
  }

  def debug(msg: String): Unit = {
    logger.debug(jmMessage(msg))
  }

  def info(msg: String): Unit = {
    logger.info(jmMessage(msg))
  }

  def error(msg: String): Unit = {
    logger.error(jmMessage(msg))
  }

  def warn(msg: String): Unit = {
    logger.warn(jmMessage(msg))
  }
}
