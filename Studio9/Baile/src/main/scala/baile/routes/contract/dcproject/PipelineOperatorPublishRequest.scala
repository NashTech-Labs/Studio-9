package baile.routes.contract.dcproject

import play.api.libs.json.{ Json, Reads }

case class PipelineOperatorPublishRequest(
  id: String,
  categoryId: String
)

object PipelineOperatorPublishRequest {

  implicit val PipelineOperatorPublishRequestReads: Reads[PipelineOperatorPublishRequest] =
    Json.reads[PipelineOperatorPublishRequest]

}
