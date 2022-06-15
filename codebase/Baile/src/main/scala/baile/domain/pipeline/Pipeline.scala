package baile.domain.pipeline

import java.time.Instant
import java.util.UUID

import baile.domain.asset.Asset

case class Pipeline(
  name: String,
  ownerId: UUID,
  status: PipelineStatus,
  created: Instant,
  updated: Instant,
  description: Option[String],
  steps: Seq[PipelineStepInfo],
  inLibrary: Boolean
) extends Asset[PipelineStatus]
