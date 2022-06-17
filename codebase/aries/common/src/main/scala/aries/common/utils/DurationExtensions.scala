package aries.common.utils

import scala.concurrent.duration.{ Duration, FiniteDuration }

trait DurationExtensions {

  implicit class DurationExtensions(d: java.time.Duration) {
    def toScala: FiniteDuration = {
      Duration.fromNanos(d.toNanos)
    }
  }

  implicit def toFiniteDuration(d: java.time.Duration): FiniteDuration = {
    Duration.fromNanos(d.toNanos)
  }

}

object DurationExtensions extends DurationExtensions

