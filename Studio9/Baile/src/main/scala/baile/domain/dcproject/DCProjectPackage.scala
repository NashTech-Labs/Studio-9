package baile.domain.dcproject

import java.time.Instant
import java.util.UUID

import baile.domain.common.Version

case class DCProjectPackage(
  ownerId: Option[UUID],
  dcProjectId: Option[String],
  name: String,
  version: Option[Version],
  location: Option[String],
  created: Instant,
  description: Option[String],
  isPublished: Boolean
)
