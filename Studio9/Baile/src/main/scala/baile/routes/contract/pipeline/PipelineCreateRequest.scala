package baile.routes.contract.pipeline

import play.api.libs.json.{ Json, Reads }

case class PipelineCreateRequest(
  name: Option[String],
  description: Option[String],
  inLibrary: Option[Boolean],
  steps: Seq[PipelineStepInfo]
)

object PipelineCreateRequest {

  implicit val PipelineCreateRequestReads: Reads[PipelineCreateRequest] =
    Json.reads[PipelineCreateRequest]

}
