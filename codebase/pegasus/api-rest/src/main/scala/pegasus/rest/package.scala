package pegasus

import java.lang.System.{ currentTimeMillis => now }

import akka.event.LoggingAdapter

package object rest {

  def time[T](f: => T, actionName: => String)(implicit logger: LoggingAdapter): T = {
    val start = now
    try {
      f
    } finally {
      logger.info(s"$actionName elapsed: " + (now - start) + " ms")
    }
  }
}
