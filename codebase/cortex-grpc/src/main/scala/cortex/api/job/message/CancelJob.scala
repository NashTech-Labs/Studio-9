package cortex.api.job.message

import play.api.libs.json._

case object CancelJob extends JobMessagePayload {

  override val payloadType: String = "CANCEL_JOB_PAYLOAD"

  implicit val format: OFormat[CancelJob.type] = new OFormat[CancelJob.type] {

    override def writes(o: CancelJob.type): JsObject = JsObject.empty

    override def reads(json: JsValue): JsResult[CancelJob.type] = Json.fromJson[JsObject](json).map(_ => CancelJob)

  }

}
