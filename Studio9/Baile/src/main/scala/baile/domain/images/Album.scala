package baile.domain.images

import java.time.Instant
import java.util.UUID

import baile.domain.asset.Asset

case class Album(
  ownerId: UUID,
  name: String,
  status: AlbumStatus,
  `type`: AlbumType,
  labelMode: AlbumLabelMode,
  created: Instant,
  updated: Instant,
  inLibrary: Boolean,
  picturesPrefix: String,
  video: Option[Video] = None,
  description: Option[String],
  augmentationTimeSpentSummary: Option[AugmentationTimeSpentSummary]
) extends Asset[AlbumStatus]
