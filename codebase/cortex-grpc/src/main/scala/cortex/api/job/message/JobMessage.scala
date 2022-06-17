package cortex.api.job.message

import play.api.libs.json.{ Json, OFormat }

case class JobMessage(meta: JobMessageMeta, payload: JobMessagePayload = EmptyPayload) {
  val id: String = meta.jobId.toString
}

object JobMessage {

  implicit val format: OFormat[JobMessage] = Json.format[JobMessage]

}
