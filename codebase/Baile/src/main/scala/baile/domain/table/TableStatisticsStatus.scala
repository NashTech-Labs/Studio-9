package baile.domain.table

sealed trait TableStatisticsStatus

object TableStatisticsStatus {

  case object Pending extends TableStatisticsStatus

  case object Error extends TableStatisticsStatus

  case object Done extends TableStatisticsStatus

}
