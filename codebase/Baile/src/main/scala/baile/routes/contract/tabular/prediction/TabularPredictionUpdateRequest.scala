package baile.routes.contract.tabular.prediction

import play.api.libs.json.{ Json, Reads }

case class TabularPredictionUpdateRequest(
  name: Option[String],
  description: Option[String]
)

object TabularPredictionUpdateRequest {
  implicit val TabularPredictionUpdateRequestReads: Reads[TabularPredictionUpdateRequest] =
    Json.reads[TabularPredictionUpdateRequest]
}
