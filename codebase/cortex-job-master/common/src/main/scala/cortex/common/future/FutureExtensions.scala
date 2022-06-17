package cortex.common.future

import scala.concurrent.Future
import scala.util.Try

trait FutureExtensions {
  implicit class TryFutureExtensions[T](t: Try[T]) {
    def toFuture: Future[T] = Future.fromTry[T](t)
  }
}

object FutureExtensions extends FutureExtensions
