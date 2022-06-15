package baile.routes.contract.cv.prediction

import play.api.libs.json.{ Json, Reads }

case class CVPredictionUpdateRequest(
  name: Option[String],
  description: Option[String]
)

object CVPredictionUpdateRequest {
  implicit val CVPredictionUpdateRequestReads: Reads[CVPredictionUpdateRequest] =
    Json.reads[CVPredictionUpdateRequest]
}
