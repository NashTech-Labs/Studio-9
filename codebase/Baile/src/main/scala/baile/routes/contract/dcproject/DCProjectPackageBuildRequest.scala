package baile.routes.contract.dcproject

import baile.routes.contract.common.Version
import play.api.libs.json.{ Json, Reads }

case class DCProjectPackageBuildRequest(
  name: Option[String],
  version: Version,
  description: Option[String],
  analyzePipelineOperators: Option[Boolean]
)

object DCProjectPackageBuildRequest {

  implicit val DCProjectPackageBuildRequestReads: Reads[DCProjectPackageBuildRequest] =
    Json.reads[DCProjectPackageBuildRequest]

}
