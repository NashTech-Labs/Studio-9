package baile.routes.contract.pipeline

import play.api.libs.json.{ Json, Reads }

case class PipelineUpdateRequest(
  name: Option[String],
  description: Option[String],
  steps: Option[Seq[PipelineStepInfo]]
)

object PipelineUpdateRequest {

  implicit val PipelineUpdateRequestReads: Reads[PipelineUpdateRequest] =
    Json.reads[PipelineUpdateRequest]

}
