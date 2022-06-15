package baile.domain.experiment

import java.time.Instant
import java.util.UUID

import baile.domain.asset.Asset
import baile.domain.experiment.pipeline.ExperimentPipeline
import baile.domain.experiment.result.ExperimentResult

case class Experiment(
  name: String,
  ownerId: UUID,
  description: Option[String],
  status: ExperimentStatus,
  pipeline: ExperimentPipeline,
  result: Option[ExperimentResult],
  created: Instant,
  updated: Instant
) extends Asset[ExperimentStatus] {
  override val inLibrary: Boolean = true
}
