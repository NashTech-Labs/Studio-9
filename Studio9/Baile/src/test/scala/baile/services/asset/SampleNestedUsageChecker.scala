package baile.services.asset

import baile.domain.asset.AssetReference
import baile.domain.usermanagement.User
import cats.data.EitherT

import scala.concurrent.{ ExecutionContext, Future }

class SampleNestedUsageChecker(implicit val ec: ExecutionContext) extends NestedUsageChecker {

  override def checkNestedUsage(
    assetReference: AssetReference,
    user: User
  ): EitherT[Future, Unit, Unit] = assetOccupiedIf(false)

}
