package baile.routes.contract.project

import baile.daocommons.WithId
import baile.domain.project.Folder
import play.api.libs.json.{ Json, OWrites }

case class ProjectFolderResponse(
  id: String,
  path: String
)

object ProjectFolderResponse {
  implicit val ProjectFolderResponseWrites: OWrites[ProjectFolderResponse] = Json.writes[ProjectFolderResponse]

  def fromDomain(folder: WithId[Folder]): ProjectFolderResponse =
    ProjectFolderResponse(
      id = folder.id,
      path = folder.entity.path
    )
}
