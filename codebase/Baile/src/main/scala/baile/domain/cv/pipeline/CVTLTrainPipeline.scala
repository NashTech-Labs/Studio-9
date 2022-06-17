package baile.domain.cv.pipeline

import baile.domain.asset.{ AssetReference, AssetType }
import baile.domain.experiment.pipeline.ExperimentPipeline

case class CVTLTrainPipeline(
  stepOne: CVTLTrainStep1Params,
  stepTwo: Option[CVTLTrainStep2Params]
) extends ExperimentPipeline {

  override def getAssetReferences: Seq[AssetReference] =
    Seq(
      Some(stepOne.inputAlbumId),
      stepOne.testInputAlbumId,
      stepTwo.map(_.inputAlbumId),
      stepTwo.flatMap(_.testInputAlbumId)
    ).collect {
      case Some(albumId) => AssetReference(albumId, AssetType.Album)
    }

}
