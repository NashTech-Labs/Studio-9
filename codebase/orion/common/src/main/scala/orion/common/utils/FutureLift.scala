package orion.common.utils

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }

object FutureLift {
  private def recover[T](futures: Seq[Future[T]])(implicit ec: ExecutionContext) =
    futures.map(_.map { Success(_) }.recover { case t => Failure(t) })

  def lift[T](futures: Seq[Future[T]])(implicit ec: ExecutionContext): Future[Seq[Try[T]]] =
    Future.sequence(recover(futures))
}

