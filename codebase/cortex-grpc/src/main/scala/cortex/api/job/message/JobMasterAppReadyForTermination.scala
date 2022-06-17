package cortex.api.job.message

import play.api.libs.json._

case object JobMasterAppReadyForTermination extends JobMessagePayload {

  override val payloadType: String = "JOB_MASTER_APP_READY_FOR_TERMINATION_PAYLOAD"

  implicit val format: OFormat[JobMasterAppReadyForTermination.type] =
    new OFormat[JobMasterAppReadyForTermination.type] {

    override def writes(emptyPayload: JobMasterAppReadyForTermination.type): JsObject = JsObject.empty

    override def reads(json: JsValue): JsResult[JobMasterAppReadyForTermination.type] =
      Json.fromJson[JsObject](json).map(_ => JobMasterAppReadyForTermination)

  }

}
