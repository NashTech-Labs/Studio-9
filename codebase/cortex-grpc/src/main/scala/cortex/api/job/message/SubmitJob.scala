package cortex.api.job.message

import play.api.libs.json.{ Json, OFormat }

case class SubmitJob(inputPath: String) extends JobMessagePayload {
  override val payloadType: String = "SUBMIT_JOB_PAYLOAD"
}

object SubmitJob {

  implicit val format: OFormat[SubmitJob] = Json.format[SubmitJob]

}
