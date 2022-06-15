package baile.domain.experiment

import baile.domain.asset.AssetStatus

sealed trait ExperimentStatus extends AssetStatus

object ExperimentStatus {

  case object Running extends ExperimentStatus
  case object Completed extends ExperimentStatus
  case object Error extends ExperimentStatus
  case object Cancelled extends ExperimentStatus

}
