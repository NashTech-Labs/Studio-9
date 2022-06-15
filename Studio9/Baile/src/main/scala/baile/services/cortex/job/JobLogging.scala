package baile.services.cortex.job

import java.util.UUID

import akka.event.LoggingAdapter

/**
 * Simple helper to log all cortex-job related information
 */
object JobLogging {

  def info(jobId: UUID, message: String, step: String = "General")(implicit logger: LoggingAdapter): Unit =
    logger.info("JobId: {} - {} - {}", jobId, step, message)

  def warn(jobId: UUID, message: String, step: String = "General")(implicit logger: LoggingAdapter): Unit =
    logger.warning("JobId: {} - {} - {}", jobId, step, message)

  def error(jobId: UUID, message: String, step: String = "General")(implicit logger: LoggingAdapter): Unit =
    logger.error("JobId: {} - {} - {}", jobId, step, message)

}
