package baile.domain.cv.model

import java.time.Instant
import java.util.UUID

import baile.domain.asset.Asset
import baile.domain.common.CortexModelReference

case class CVModel(
  ownerId: UUID,
  name: String,
  description: Option[String],
  created: Instant,
  updated: Instant,
  status: CVModelStatus,
  inLibrary: Boolean,
  cortexFeatureExtractorReference: Option[CortexModelReference],
  cortexModelReference: Option[CortexModelReference],
  `type`: CVModelType,
  classNames: Option[Seq[String]],
  featureExtractorId: Option[String],
  experimentId: Option[String]
) extends Asset[CVModelStatus]
