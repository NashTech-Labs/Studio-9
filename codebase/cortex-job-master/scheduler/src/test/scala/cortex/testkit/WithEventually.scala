package cortex.testkit

import org.scalatest.concurrent.Eventually
import org.scalatest.concurrent.PatienceConfiguration.{ Interval, Timeout }
import org.scalatest.time.{ Seconds, Span }

trait WithEventually extends Eventually {
  protected val evTimeout = Timeout(Span(60 * 5, Seconds))
  protected val evInterval = Interval(Span(1, Seconds))

  def eventually[T](block: => T): T = {
    eventually[T](evTimeout, evInterval)(block)
  }
}
