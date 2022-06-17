package baile.domain.onlinejob

import java.time.Instant
import java.util.UUID

import baile.domain.asset.Asset

case class OnlineJob(
  ownerId: UUID,
  name: String,
  status: OnlineJobStatus,
  options: OnlineJobOptions,
  enabled: Boolean,
  created: Instant,
  updated: Instant,
  description: Option[String]
) extends Asset[OnlineJobStatus] {
  override val inLibrary: Boolean = true
}
