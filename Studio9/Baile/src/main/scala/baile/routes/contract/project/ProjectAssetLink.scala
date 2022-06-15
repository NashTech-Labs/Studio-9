package baile.routes.contract.project

import play.api.libs.json.{ Json, Reads }

case class ProjectAssetLink(folderId: Option[String])

object ProjectAssetLink {
  implicit val ProjectAssetLinkReads: Reads[ProjectAssetLink] =
    Json.reads[ProjectAssetLink]
}
