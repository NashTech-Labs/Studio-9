package baile.routes.contract.dcproject

import java.time.Instant

import baile.daocommons.WithId
import baile.domain.dcproject.{ DCProject, DCProjectStatus }
import baile.routes.contract.common.Version
import play.api.libs.json.{ Json, OWrites }

case class DCProjectResponse(
  id: String,
  name: String,
  created: Instant,
  updated: Instant,
  ownerId: String,
  description: Option[String],
  status: DCProjectStatus,
  packageName: Option[String],
  packageVersion: Option[Version]
)

object DCProjectResponse {
  implicit val DCProjectResponseWrites: OWrites[DCProjectResponse] = Json.writes[DCProjectResponse]

  def fromDomain(in: WithId[DCProject]): DCProjectResponse = in match {
    case WithId(project, id) => DCProjectResponse(
      id = id,
      name = project.name,
      created = project.created,
      updated = project.updated,
      ownerId = project.ownerId.toString,
      description = project.description,
      status = project.status,
      packageName = project.packageName,
      packageVersion = project.latestPackageVersion.map(Version.fromDomain)
    )
  }
}
