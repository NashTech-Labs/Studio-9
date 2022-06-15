package cortex.api.job.message

import java.util.Date

import play.api.libs.json.{ Json, OFormat }

case class JobResultFailure(
  completedAt: Date,
  errorCode: String,
  errorMessage: String,
  errorDetails: Map[String, String] = Map.empty[String, String]
) extends JobResult {
  override val payloadType: String = "JOB_RESULT_FAILURE_PAYLOAD"
}

object JobResultFailure {

  implicit val format: OFormat[JobResultFailure] = Json.format[JobResultFailure]

}
