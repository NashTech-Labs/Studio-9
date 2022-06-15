package baile.services.asset.sharing

import baile.domain.asset.AssetReference
import baile.domain.asset.sharing.SharedResource
import cats.data.EitherT
import cats.implicits._

import scala.concurrent.{ ExecutionContext, Future }

trait SharedAccessChecker {
  implicit val ec: ExecutionContext

  def checkSharedAccess(
    assetReference: AssetReference,
    sharedResource: SharedResource
  ): EitherT[Future, Unit, Unit]

  final protected def accessGrantedIf(cond: Boolean): EitherT[Future, Unit, Unit] =
    EitherT.cond[Future].apply(cond, (), ())

  final protected def accessGrantedIf(cond: Future[Boolean]): EitherT[Future, Unit, Unit] =
    EitherT(cond.map(Either.cond(_, (), ())))

  final protected val accessDenied: EitherT[Future, Unit, Unit] =
    EitherT.leftT[Future, Unit](())
}
