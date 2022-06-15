package baile.routes.contract.project

import java.time.Instant

import baile.daocommons.WithId
import baile.domain.asset.AssetType
import baile.domain.project.Project
import play.api.libs.json.{ Json, OWrites }

case class ExtendedProjectResponse(
  id: String,
  name: String,
  created: Instant,
  updated: Instant,
  ownerId: String,
  modelsCount: Int,
  flowsCount: Int,
  tablesCount: Int
)

object ExtendedProjectResponse {
  implicit val ExtendedProjectResponseWrites: OWrites[ExtendedProjectResponse] = Json.writes[ExtendedProjectResponse]

  def fromDomain(in: WithId[Project]): ExtendedProjectResponse = {
    def countAssets(assetType: AssetType) = in.entity.assets.count(_.assetReference.`type` == assetType)
    in match {
      case WithId(project, id) => ExtendedProjectResponse(
        id = id,
        name = project.name,
        created = project.created,
        updated = project.updated,
        ownerId = project.ownerId.toString,
        modelsCount = countAssets(AssetType.CvModel),
        flowsCount = countAssets(AssetType.Flow),
        tablesCount = countAssets(AssetType.Table)
      )
    }
  }
}
