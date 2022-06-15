package baile.services.tabular.prediction

import baile.dao.asset.Filters.OwnerIdIs
import baile.dao.tabular.prediction.TabularPredictionDao
import baile.dao.tabular.prediction.TabularPredictionDao.{ TableIdIs, TabularModelIdIs }
import baile.daocommons.filters.Filter
import baile.domain.asset.{ AssetReference, AssetType }
import baile.domain.usermanagement.User
import baile.services.asset.NestedUsageChecker

trait TabularPredictionNestedUsage extends NestedUsageChecker {

  protected val tabularPredictionDao: TabularPredictionDao

  abstract override def checkNestedUsage(
    assetReference: AssetReference,
    user: User
  ): NestedUsageResult = {
    super.checkNestedUsage(assetReference, user) andThen {
      assetReference.`type` match {
        case AssetType.Table => checkUsageInTabularPrediction(TableIdIs(assetReference.id), user)
        case AssetType.TabularModel => checkUsageInTabularPrediction(TabularModelIdIs(assetReference.id), user)
        case _ => assetIsFree
      }
    }
  }

  final protected def checkUsageInTabularPrediction(filter: Filter, user: User): NestedUsageResult =
    assetOccupiedIf {
      tabularPredictionDao.count(OwnerIdIs(user.id) && filter).map(_ > 0)
    }

}

