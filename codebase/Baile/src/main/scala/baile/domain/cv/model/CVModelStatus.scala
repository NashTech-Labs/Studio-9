package baile.domain.cv.model

import baile.domain.asset.AssetStatus

sealed trait CVModelStatus extends AssetStatus

object CVModelStatus {
  case object Saving extends CVModelStatus
  case object Active extends CVModelStatus
  case object Training extends CVModelStatus
  case object Pending extends CVModelStatus
  case object Predicting extends CVModelStatus
  case object Error extends CVModelStatus
  case object Cancelled extends CVModelStatus
}
