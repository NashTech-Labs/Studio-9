package cortex.api.job.message

import play.api.libs.json._

case object EmptyPayload extends JobMessagePayload {

  override val payloadType: String = "EMPTY_PAYLOAD"

  implicit val format: OFormat[EmptyPayload.type] = new OFormat[EmptyPayload.type] {

    override def writes(emptyPayload: EmptyPayload.type): JsObject = JsObject.empty

    override def reads(json: JsValue): JsResult[EmptyPayload.type] =
      Json.fromJson[JsObject](json).map(_ => EmptyPayload)

  }

}
