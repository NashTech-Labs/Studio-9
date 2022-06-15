package baile.domain.tabular.prediction

import baile.domain.asset.AssetStatus

sealed trait TabularPredictionStatus extends AssetStatus

object TabularPredictionStatus {

  case object New extends TabularPredictionStatus

  case object Running extends TabularPredictionStatus

  case object Error extends TabularPredictionStatus

  case object Done extends TabularPredictionStatus

}
