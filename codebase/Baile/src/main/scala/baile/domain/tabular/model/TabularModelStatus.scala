package baile.domain.tabular.model

import baile.domain.asset.AssetStatus

sealed trait TabularModelStatus extends AssetStatus

object TabularModelStatus {
  case object Active extends TabularModelStatus
  case object Training extends TabularModelStatus
  case object Predicting extends TabularModelStatus
  case object Error extends TabularModelStatus
  case object Cancelled extends TabularModelStatus
  case object Saving extends TabularModelStatus
}
