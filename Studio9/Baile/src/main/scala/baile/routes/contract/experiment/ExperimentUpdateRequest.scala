package baile.routes.contract.experiment

import play.api.libs.json.{ Json, Reads }

case class ExperimentUpdateRequest(
  name: Option[String],
  description: Option[String]
)

object ExperimentUpdateRequest {
  implicit val ExperimentUpdateRequestReads: Reads[ExperimentUpdateRequest] = Json.reads[ExperimentUpdateRequest]
}
