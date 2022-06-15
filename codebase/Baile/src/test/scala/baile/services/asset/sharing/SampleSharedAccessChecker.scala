package baile.services.asset.sharing

import baile.domain.asset.AssetReference
import baile.domain.asset.sharing.SharedResource
import cats.data.EitherT

import scala.concurrent.{ ExecutionContext, Future }

class SampleSharedAccessChecker(implicit val ec: ExecutionContext) extends SharedAccessChecker {

  override def checkSharedAccess(
    assetReference: AssetReference,
    sharedResource: SharedResource
  ): EitherT[Future, Unit, Unit] = accessDenied

}
