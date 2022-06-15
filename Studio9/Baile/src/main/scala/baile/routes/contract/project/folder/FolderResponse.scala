package baile.routes.contract.project.folder

import baile.daocommons.WithId
import baile.domain.project.Folder
import play.api.libs.json.{ Json, OWrites }

case class FolderResponse(id: String, path: String)

object FolderResponse {

  implicit val ExtendedProjectResponseWrites: OWrites[FolderResponse] = Json.writes[FolderResponse]

  def fromDomain(in: WithId[Folder]): FolderResponse = {
    FolderResponse(
      id = in.id,
      path = in.entity.path
    )
  }

}
