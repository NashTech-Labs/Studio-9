package taurus

import java.util.Date

import org.joda.time.{ DateTime, DateTimeZone }

package object common {

  val nowDateTime: DateTime = DateTime.now(DateTimeZone.UTC)

  val now: Date = nowDateTime.toDate

  implicit class ToDateTimeHelper(val date: DateTime) extends AnyVal {
    def toUtcIso: String = Option(date).fold("")(_.withZone(DateTimeZone.UTC).toDateTimeISO.toString)
  }

  implicit class ToDateHelper(val date: Date) extends AnyVal {
    def toUtcIso: String = new DateTime(date).toUtcIso
  }

  implicit class ToAnyHelper(val any: Any) extends AnyVal {
    def isEmpty: Boolean = Option(any).fold(true)(_ => false)

    def isDefined: Boolean = !isEmpty

    def toStr: String = if (isDefined) any.toString.trim else ""
  }
}
