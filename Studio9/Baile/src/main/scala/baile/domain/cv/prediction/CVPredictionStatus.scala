package baile.domain.cv.prediction

import baile.domain.asset.AssetStatus

sealed trait CVPredictionStatus extends AssetStatus

object CVPredictionStatus {

  case object New extends CVPredictionStatus
  case object Running extends CVPredictionStatus
  case object Error extends CVPredictionStatus
  case object Done extends CVPredictionStatus

}
