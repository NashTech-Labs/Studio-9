package baile.domain.experiment.result

import baile.domain.asset.AssetReference

trait ExperimentResult {

  def getAssetReferences: Seq[AssetReference]

}

