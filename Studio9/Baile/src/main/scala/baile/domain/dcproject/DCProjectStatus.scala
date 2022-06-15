package baile.domain.dcproject

import baile.domain.asset.AssetStatus

sealed trait DCProjectStatus extends AssetStatus

object DCProjectStatus {

  case object Idle extends DCProjectStatus

  case object Interactive extends DCProjectStatus

  case object Building extends DCProjectStatus

}
