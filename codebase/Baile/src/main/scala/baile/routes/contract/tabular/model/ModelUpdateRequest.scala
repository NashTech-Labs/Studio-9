package baile.routes.contract.tabular.model

import play.api.libs.json.{ Json, Reads }

case class ModelUpdateRequest(
  name: Option[String],
  description: Option[String]
)

object ModelUpdateRequest {
  implicit val ModelUpdateRequestReads: Reads[ModelUpdateRequest] = Json.reads[ModelUpdateRequest]
}
