package baile.routes.contract.project

import play.api.libs.json.{ Json, Reads }

case class ProjectCreateOrUpdateRequest(name: String)

object ProjectCreateOrUpdateRequest {
  implicit val ProjectCreateOrUpdateReads: Reads[ProjectCreateOrUpdateRequest] =
    Json.reads[ProjectCreateOrUpdateRequest]
}
