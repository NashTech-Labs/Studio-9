package sqlserver.utils

import java.util.concurrent.TimeUnit

import scala.concurrent.duration.FiniteDuration

object DurationConverter {

  def toScalaFiniteDuration(javaDuration: java.time.Duration): FiniteDuration =
    FiniteDuration(javaDuration.toNanos, TimeUnit.NANOSECONDS)

}
