package baile.domain.job

case class CortexJobTimeSpentSummary(
  tasksQueuedTime: Long,
  jobTimeInfo: CortexTimeInfo,
  tasksTimeInfo: Seq[CortexTaskTimeInfo]
) {

  def calculateTotalJobTime: Long = jobTimeInfo.completedAt.getEpochSecond - jobTimeInfo.startedAt.getEpochSecond

}
