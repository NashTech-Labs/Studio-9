package baile.routes.contract.cv.prediction

import play.api.libs.json.{ Json, Reads }

case class CVPredictionCreateRequest(
  modelId: String,
  name: Option[String],
  outputAlbumName: Option[String],
  input: String,
  options: Option[CVModelPredictOptionsRequest],
  evaluate: Option[Boolean],
  description: Option[String]
)

object CVPredictionCreateRequest {
  implicit val CVPredictionCreateRequestReads: Reads[CVPredictionCreateRequest] = Json.reads[CVPredictionCreateRequest]
}
