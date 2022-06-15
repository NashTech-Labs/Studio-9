package baile.daocommons.sorting

sealed trait Direction

object Direction {
  case object Ascending extends Direction
  case object Descending extends Direction
}
