package baile.domain.asset.sharing

import java.time.Instant
import java.util.UUID

import baile.domain.asset.AssetType

case class SharedResource(
  ownerId: UUID,
  name: Option[String] = None,
  created: Instant,
  updated: Instant,
  recipientId: Option[UUID] = None,
  recipientEmail: Option[String] = None,
  assetType: AssetType,
  assetId: String
)
