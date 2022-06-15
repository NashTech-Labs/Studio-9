package baile.domain.onlinejob

import baile.domain.asset.AssetStatus

sealed trait OnlineJobStatus extends AssetStatus

object OnlineJobStatus {

  case object Running extends OnlineJobStatus

  case object Idle extends OnlineJobStatus

}
