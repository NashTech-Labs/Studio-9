package baile.routes.contract.project

import java.time.Instant

import baile.daocommons.WithId
import baile.domain.project.Project
import play.api.libs.json.{ Json, OWrites }

case class ProjectResponse(
  id: String,
  name: String,
  created: Instant,
  updated: Instant,
  ownerId: String,
  folders: Seq[ProjectFolderResponse]
)

object ProjectResponse {
  implicit val ProjectResponseWrites: OWrites[ProjectResponse] = Json.writes[ProjectResponse]

  def fromDomain(in: WithId[Project]): ProjectResponse = in match {
    case WithId(project, id) => ProjectResponse(
      id = id,
      name = project.name,
      created = project.created,
      updated = project.updated,
      ownerId = project.ownerId.toString,
      folders = project.folders.map(ProjectFolderResponse.fromDomain)
    )
  }
}
