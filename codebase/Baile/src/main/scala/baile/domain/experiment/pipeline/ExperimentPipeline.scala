package baile.domain.experiment.pipeline

import baile.domain.asset.AssetReference

trait ExperimentPipeline {

  def getAssetReferences: Seq[AssetReference]

}
