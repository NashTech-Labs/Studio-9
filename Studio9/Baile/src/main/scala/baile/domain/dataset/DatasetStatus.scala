package baile.domain.dataset

import baile.domain.asset.AssetStatus

sealed trait DatasetStatus extends AssetStatus

object DatasetStatus {

  case object Importing extends DatasetStatus

  case object Exporting extends DatasetStatus

  case object Active extends DatasetStatus

  case object Failed extends DatasetStatus

}
