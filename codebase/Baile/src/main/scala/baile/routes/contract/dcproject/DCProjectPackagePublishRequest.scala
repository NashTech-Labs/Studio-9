package baile.routes.contract.dcproject

import play.api.libs.json.{ Json, Reads }

case class DCProjectPackagePublishRequest(
  pipelineOperators: Seq[PipelineOperatorPublishRequest]
)

object DCProjectPackagePublishRequest {

  implicit val DCProjectPackagePublishRequestReads: Reads[DCProjectPackagePublishRequest] =
    Json.reads[DCProjectPackagePublishRequest]

}
