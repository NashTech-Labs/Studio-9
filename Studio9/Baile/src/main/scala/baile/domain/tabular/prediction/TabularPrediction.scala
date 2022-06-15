package baile.domain.tabular.prediction

import java.time.Instant
import java.util.UUID

import baile.domain.asset.Asset

case class TabularPrediction(
  ownerId: UUID,
  name: String,
  status: TabularPredictionStatus,
  created: Instant,
  updated: Instant,
  description: Option[String],

  modelId: String,
  inputTableId: String,
  outputTableId: String,
  columnMappings: Seq[ColumnMapping]
) extends Asset[TabularPredictionStatus] {
  override val inLibrary: Boolean = true
}
