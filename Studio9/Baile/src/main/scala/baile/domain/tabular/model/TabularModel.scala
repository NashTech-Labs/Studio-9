package baile.domain.tabular.model

import java.time.Instant
import java.util.UUID

import baile.domain.asset.Asset
import baile.domain.common.{ ClassReference, CortexModelReference }

case class TabularModel(
  ownerId: UUID,
  name: String,
  predictorColumns: Seq[ModelColumn],
  responseColumn: ModelColumn,
  classNames: Option[Seq[String]],
  classReference: ClassReference,
  cortexModelReference: Option[CortexModelReference],
  inLibrary: Boolean,
  status: TabularModelStatus,
  created: Instant,
  updated: Instant,
  description: Option[String],
  experimentId: Option[String]
) extends Asset[TabularModelStatus]
