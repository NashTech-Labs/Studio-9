package baile.domain.asset

sealed trait AssetScope

object AssetScope {
  case object All extends AssetScope
  case object Personal extends AssetScope
  case object Shared extends AssetScope
}
