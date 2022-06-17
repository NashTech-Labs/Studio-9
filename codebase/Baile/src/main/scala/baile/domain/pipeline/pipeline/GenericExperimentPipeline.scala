package baile.domain.pipeline.pipeline

import baile.domain.asset.AssetReference
import baile.domain.experiment.pipeline.ExperimentPipeline
import baile.domain.pipeline.PipelineStep

case class GenericExperimentPipeline(
  steps: Seq[PipelineStep],
  assets: Seq[AssetReference]
) extends ExperimentPipeline {

  override def getAssetReferences: Seq[AssetReference] = assets

}
