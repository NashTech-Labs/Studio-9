package cortex.api.job.message

import play.api.libs.json._

case object CleanUpResources extends JobMessagePayload {

  override val payloadType: String = "CLEAN_UP_RESOURCES_PAYLOAD"

  implicit val format: OFormat[CleanUpResources.type] =
    new OFormat[CleanUpResources.type] {

      override def writes(emptyPayload: CleanUpResources.type): JsObject = JsObject.empty

      override def reads(json: JsValue): JsResult[CleanUpResources.type] =
        Json.fromJson[JsObject](json).map(_ => CleanUpResources)

    }

}
