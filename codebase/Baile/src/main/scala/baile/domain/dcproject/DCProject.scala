package baile.domain.dcproject

import java.time.Instant
import java.util.UUID

import baile.domain.asset.Asset
import baile.domain.common.Version

case class DCProject(
  ownerId: UUID,
  name: String,
  status: DCProjectStatus,
  created: Instant,
  updated: Instant,
  description: Option[String],
  basePath: String,
  packageName: Option[String],
  latestPackageVersion: Option[Version]
) extends Asset[DCProjectStatus] {
  override val inLibrary: Boolean = true
}
