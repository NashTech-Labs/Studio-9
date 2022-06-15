package baile.domain.pipeline.result

import baile.domain.asset.AssetReference
import baile.domain.experiment.result.ExperimentResult

case class GenericExperimentResult(
  steps: Seq[GenericExperimentStepResult]
) extends ExperimentResult {

  override def getAssetReferences: Seq[AssetReference] =
    for {
      step <- steps
      assetReference <- step.assets
    } yield assetReference

}
