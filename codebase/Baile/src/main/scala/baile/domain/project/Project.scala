package baile.domain.project

import java.time.Instant
import java.util.UUID

import baile.daocommons.WithId

case class Project(
  name: String,
  created: Instant,
  updated: Instant,
  ownerId: UUID,
  folders: Seq[WithId[Folder]],
  assets: Seq[ProjectAssetReference]
)
