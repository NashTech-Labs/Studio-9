package cortex.api.job.message

import java.util.Date

import play.api.libs.json.{ Json, OFormat }

case class JobStarted(date: Date) extends JobMessagePayload {
  override val payloadType: String = "JOB_STARTED_PAYLOAD"
}

object JobStarted {

  implicit val format: OFormat[JobStarted] = Json.format[JobStarted]

}
