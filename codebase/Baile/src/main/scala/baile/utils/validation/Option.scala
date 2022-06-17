package baile.utils.validation

import cats.data.EitherT
import cats.implicits._

import scala.concurrent.{ ExecutionContext, Future }


object Option {

  implicit class OptionOps[T](option: Option[T]) {

    def validate[E](f: T => Either[E, Unit]): Either[E, Unit] =
      option match {
        case None => Right(())
        case Some(value) => f(value)
      }

    def validate[E](f: T => Future[Either[E, Unit]])(implicit ec: ExecutionContext): Future[Either[E, Unit]] =
      option match {
        case None => Future.successful(Right(()))
        case Some(value) => f(value)
      }

    def validate[E](f: T => EitherT[Future, E, Unit])(implicit ec: ExecutionContext): EitherT[Future, E, Unit] = {
      option match {
        case None => EitherT.rightT[Future, E](())
        case Some(value) => f(value)
      }
    }

  }

}
