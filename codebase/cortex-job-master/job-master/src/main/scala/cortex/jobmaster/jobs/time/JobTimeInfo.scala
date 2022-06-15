package cortex.jobmaster.jobs.time

import cortex.TaskTimeInfo
import cortex.jobmaster.jobs.time.JobTimeInfo._

import scala.concurrent.duration.{ FiniteDuration, MILLISECONDS }

case class JobTimeInfo(jobTasksTimeInfo: JobTimeInfo.TasksTimeInfo) {
  lazy val jobTasksQueuedTime: FiniteDuration = {
    require(
      jobTasksTimeInfo.forall(tti => tti.startedAt.isDefined && tti.completedAt.isDefined),
      "Time when a task started and completed should be defined to calculate queued time"
    )

    val changeLog: Seq[(Long, StateChange)] = jobTasksTimeInfo.flatMap { task =>
      Seq(
        (task.submittedAt.getTime, TaskSubmitted),
        (task.startedAt.get.getTime, TaskStarted),
        (task.completedAt.get.getTime, TaskCompleted)
      )
    }.sortBy {
      case (time, _) => time
    }

    val (overallQueuedTime, _, _, _) = changeLog.foldLeft((0L, 0L, 0, 0)) {
      case ((queuedTimeSoFar, previousTime, submittedCount, runningCount), (changeTime, stateChange)) =>
        val timePeriod = changeTime - previousTime
        val newQueuedTime = if (submittedCount > 0 && runningCount == 0) {
          queuedTimeSoFar + timePeriod
        } else {
          queuedTimeSoFar
        }

        stateChange match {
          case TaskSubmitted => (newQueuedTime, changeTime, submittedCount + 1, runningCount)
          case TaskStarted   => (newQueuedTime, changeTime, submittedCount - 1, runningCount + 1)
          case TaskCompleted => (newQueuedTime, changeTime, submittedCount, runningCount - 1)
        }
    }

    FiniteDuration(overallQueuedTime, MILLISECONDS)
  }
}

object JobTimeInfo {
  type TasksTimeInfo = Seq[TaskTimeInfo]

  private sealed trait StateChange {}
  private case object TaskSubmitted extends StateChange
  private case object TaskStarted extends StateChange
  private case object TaskCompleted extends StateChange
}
