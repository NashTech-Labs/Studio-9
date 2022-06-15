package baile.domain.table

import baile.domain.asset.AssetStatus

sealed trait TableStatus extends AssetStatus

object TableStatus {

  case object Saving extends TableStatus

  case object Active extends TableStatus

  case object Inactive extends TableStatus

  case object Error extends TableStatus

}
