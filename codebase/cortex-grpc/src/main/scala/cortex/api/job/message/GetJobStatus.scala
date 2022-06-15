package cortex.api.job.message

import play.api.libs.json._

case object GetJobStatus extends JobMessagePayload {

  override val payloadType: String = "GET_JOB_STATUS_PAYLOAD"

  implicit val format: OFormat[GetJobStatus.type] =
    new OFormat[GetJobStatus.type] {

      override def writes(emptyPayload: GetJobStatus.type): JsObject = JsObject.empty

      override def reads(json: JsValue): JsResult[GetJobStatus.type] =
        Json.fromJson[JsObject](json).map(_ => GetJobStatus)

    }

}
