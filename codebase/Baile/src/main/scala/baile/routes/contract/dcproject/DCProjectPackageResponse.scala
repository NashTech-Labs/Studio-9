package baile.routes.contract.dcproject

import java.time.Instant

import baile.daocommons.WithId
import baile.domain.dcproject.DCProjectPackage
import baile.routes.contract.common.Version
import play.api.libs.json.{ Json, OWrites }

case class DCProjectPackageResponse(
  id: String,
  name: String,
  created: Instant,
  ownerId: Option[String],
  description: Option[String],
  version: Option[Version],
  location: Option[String],
  dcProjectId: Option[String],
  isPublished: Boolean
)

object DCProjectPackageResponse {
  implicit val DCProjectPackageResponseWrites: OWrites[DCProjectPackageResponse] = Json.writes[DCProjectPackageResponse]

  def fromDomain(in: WithId[DCProjectPackage]): DCProjectPackageResponse = in match {
    case WithId(projectPackage, id) => DCProjectPackageResponse(
      id = id,
      name = projectPackage.name,
      created = projectPackage.created,
      ownerId = projectPackage.ownerId.map(_.toString),
      description = projectPackage.description,
      version = projectPackage.version.map(Version.fromDomain),
      location = projectPackage.location,
      dcProjectId = projectPackage.dcProjectId,
      isPublished = projectPackage.isPublished
    )
  }
}
