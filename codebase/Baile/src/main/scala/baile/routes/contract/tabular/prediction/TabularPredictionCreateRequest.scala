package baile.routes.contract.tabular.prediction

import play.api.libs.json.{ Json, Reads }

case class TabularPredictionCreateRequest(
  modelId: String,
  input: String,
  name: Option[String],
  columnMappings: Seq[SimpleMappingPair],
  description: Option[String]
)

object TabularPredictionCreateRequest {
  implicit val TabularPredictionCreateRequestReads: Reads[TabularPredictionCreateRequest] =
    Json.reads[TabularPredictionCreateRequest]
}
