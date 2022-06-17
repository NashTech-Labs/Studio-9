package taurus.common.utils

import scala.concurrent.Future
import scala.util.Try

trait TryExtensions {

  implicit class TryExtensions[+A](t: Try[A]) {

    def toFuture: Future[A] = {
      Future.fromTry(t)
    }
  }

}

object TryExtensions extends TryExtensions
