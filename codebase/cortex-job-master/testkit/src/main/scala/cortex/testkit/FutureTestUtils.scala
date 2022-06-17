package cortex.testkit

import scala.concurrent.{ Await, Future }
import scala.concurrent.duration._

trait FutureTestUtils {
  implicit class FutureAwaitUtil[T](future: Future[T]) {
    def await(duration: Duration = 15.minutes): T = {
      Await.result(future, duration)
    }
  }
}
