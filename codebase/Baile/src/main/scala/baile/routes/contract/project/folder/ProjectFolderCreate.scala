package baile.routes.contract.project.folder

import play.api.libs.json.{ Json, Reads }

case class ProjectFolderCreate(path: String)

object ProjectFolderCreate {
  implicit val ProjectFolderCreateReads: Reads[ProjectFolderCreate] =
    Json.reads[ProjectFolderCreate]
}
