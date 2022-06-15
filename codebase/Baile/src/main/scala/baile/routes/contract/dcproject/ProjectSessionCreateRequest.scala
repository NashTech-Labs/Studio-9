package baile.routes.contract.dcproject

import play.api.libs.json.{ Json, OFormat }

case class ProjectSessionCreateRequest(useGPU: Boolean)

object ProjectSessionCreateRequest {

  implicit val ProjectSessionCreateRequest: OFormat[ProjectSessionCreateRequest] =
    Json.format[ProjectSessionCreateRequest]

}
