package baile.domain.dataset

import java.time.Instant
import java.util.UUID

import baile.domain.asset.Asset

case class Dataset(
  ownerId: UUID,
  name: String,
  status: DatasetStatus,
  created: Instant,
  updated: Instant,
  description: Option[String],
  basePath: String
) extends Asset[DatasetStatus] {
  override val inLibrary: Boolean = true
}
