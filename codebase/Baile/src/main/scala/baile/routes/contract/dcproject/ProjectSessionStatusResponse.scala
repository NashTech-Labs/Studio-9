package baile.routes.contract.dcproject

import baile.domain.dcproject.{ ProjectSessionStatus, SessionStatus }
import play.api.libs.json.{ Json, OWrites }

case class ProjectSessionStatusResponse(
  status: SessionStatus,
  runningTime: Long
)

object ProjectSessionStatusResponse {
  implicit val ProjectSessionStatusResponseWrites: OWrites[ProjectSessionStatusResponse] =
    Json.writes[ProjectSessionStatusResponse]

  def fromDomain(projectSessionStatus: ProjectSessionStatus): ProjectSessionStatusResponse =
    ProjectSessionStatusResponse(
      status = projectSessionStatus.status,
      runningTime = projectSessionStatus.runningTime
    )
}
