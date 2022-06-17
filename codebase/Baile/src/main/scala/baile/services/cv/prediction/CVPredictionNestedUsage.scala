package baile.services.cv.prediction

import baile.dao.asset.Filters.OwnerIdIs
import baile.dao.cv.prediction.CVPredictionDao
import baile.dao.cv.prediction.CVPredictionDao.{ AlbumIdIs, CVModelIdIs }
import baile.daocommons.filters.Filter
import baile.domain.asset.{ AssetReference, AssetType }
import baile.domain.usermanagement.User
import baile.services.asset.NestedUsageChecker

trait CVPredictionNestedUsage extends NestedUsageChecker {

  protected val cvPredictionDao: CVPredictionDao

  abstract override def checkNestedUsage(
    assetReference: AssetReference,
    user: User
  ): NestedUsageResult = {
    super.checkNestedUsage(assetReference, user) andThen {
      assetReference.`type` match {
        case AssetType.Album => checkUsageInPrediction(AlbumIdIs(assetReference.id), user)
        case AssetType.CvModel => checkUsageInPrediction(CVModelIdIs(assetReference.id), user)
        case _ => assetIsFree
      }
    }
  }

  final protected def checkUsageInPrediction(filter: Filter, user: User): NestedUsageResult =
    assetOccupiedIf {
      cvPredictionDao.count(OwnerIdIs(user.id) && filter).map(_ > 0)
    }

}
