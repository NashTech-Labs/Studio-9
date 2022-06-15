package baile.services.cv.model

import baile.dao.asset.Filters.OwnerIdIs
import baile.dao.cv.model.CVModelDao
import baile.dao.cv.model.CVModelDao.CVFeatureExtractorIdIs
import baile.daocommons.filters.Filter
import baile.domain.asset.{ AssetReference, AssetType }
import baile.domain.usermanagement.User
import baile.services.asset.NestedUsageChecker

trait CVModelNestedUsage extends NestedUsageChecker {

  protected val cvModelDao: CVModelDao

  abstract override def checkNestedUsage(
    assetReference: AssetReference,
    user: User
  ): NestedUsageResult = {
    super.checkNestedUsage(assetReference, user) andThen {
      assetReference.`type` match {
        case AssetType.CvModel => checkUsageInCVModel(CVFeatureExtractorIdIs(assetReference.id), user)
        case _ => assetIsFree
      }
    }
  }

  final protected def checkUsageInCVModel(filter: Filter, user: User): NestedUsageResult =
    assetOccupiedIf {
      cvModelDao.count(OwnerIdIs(user.id) && filter).map(_ > 0)
    }

}
