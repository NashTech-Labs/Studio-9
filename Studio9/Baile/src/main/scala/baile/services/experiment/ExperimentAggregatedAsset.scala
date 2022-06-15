package baile.services.experiment

import baile.dao.asset.Filters.OwnerIdIs
import baile.dao.experiment.ExperimentDao
import baile.dao.experiment.SerializerDelegator.HasAssetReference
import baile.daocommons.filters.IdIs
import baile.domain.asset.sharing.SharedResource
import baile.domain.asset.{ AssetReference, AssetType }
import baile.domain.usermanagement.User
import baile.services.asset.NestedUsageChecker
import baile.services.asset.sharing.SharedAccessChecker
import cats.data.EitherT
import cats.implicits._

import scala.concurrent.Future

trait ExperimentAggregatedAsset extends NestedUsageChecker with SharedAccessChecker {

  protected val experimentDao: ExperimentDao

  abstract override def checkNestedUsage(
    assetReference: AssetReference,
    user: User
  ): NestedUsageResult =
    super.checkNestedUsage(assetReference, user) andThen {
      assetOccupiedIf {
        experimentDao.exists(OwnerIdIs(user.id) && HasAssetReference(assetReference))
      }
    }

  abstract override def checkSharedAccess(
    assetReference: AssetReference,
    sharedResource: SharedResource
  ): EitherT[Future, Unit, Unit] =
    super.checkSharedAccess(assetReference, sharedResource) orElse {
      sharedResource.assetType match {
        case AssetType.Experiment =>
          accessGrantedIf(
            experimentDao.exists(IdIs(sharedResource.assetId) && HasAssetReference(assetReference))
          )
        case _ => accessDenied
      }
    }

}
