package cortex.api.job.message

import java.util.Date

import play.api.libs.json.{ Json, OFormat }

import scala.concurrent.duration.FiniteDuration

case class JobResultSuccess(
    completedAt: Date,
    tasksTimeInfo:   Seq[TaskTimeInfo],
    tasksQueuedTime: FiniteDuration,
    outputPath: String
  ) extends JobResult {
  override val payloadType: String = "JOB_RESULT_SUCCESS_PAYLOAD"
}

object JobResultSuccess {

  implicit val format: OFormat[JobResultSuccess] = Json.format[JobResultSuccess]

}
