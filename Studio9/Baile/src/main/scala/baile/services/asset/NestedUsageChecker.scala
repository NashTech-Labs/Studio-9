package baile.services.asset

import baile.domain.asset.AssetReference
import baile.domain.usermanagement.User
import cats.data.EitherT
import cats.implicits._

import scala.concurrent.{ ExecutionContext, Future }

trait  NestedUsageChecker {
  implicit val ec: ExecutionContext

  type NestedUsageResult = EitherT[Future, Unit, Unit]

  def checkNestedUsage(
    assetReference: AssetReference,
    user: User
  ): NestedUsageResult

  // Occupied is Left
  // assetIsFree is Right
  final protected def assetOccupiedIf(cond: Boolean): NestedUsageResult =
    EitherT.cond[Future].apply(!cond, (), ())

  final protected def assetOccupiedIf(cond: Future[Boolean]): NestedUsageResult =
    EitherT.right(cond).flatMap { value => EitherT.cond.apply(!value, (), ()) }

  final protected val assetIsFree: NestedUsageResult =
    EitherT.rightT[Future, Unit](())

  implicit class NestedUsageResultHelper(result: NestedUsageResult) {
    def andThen(other: => NestedUsageResult): NestedUsageResult =
      result flatMap { _ => other }
  }

}
