package baile.domain.cv.result

import baile.domain.asset.{ AssetReference, AssetType }
import baile.domain.experiment.result.ExperimentResult

case class CVTLTrainResult(
  stepOne: CVTLTrainStepResult,
  stepTwo: Option[CVTLTrainStepResult]
) extends ExperimentResult {

  override def getAssetReferences: Seq[AssetReference] = {
    val albums = Seq(
      stepOne.outputAlbumId,
      stepOne.testOutputAlbumId,
      stepTwo.flatMap(_.outputAlbumId),
      stepTwo.flatMap(_.testOutputAlbumId)
    ).collect {
      case Some(albumId) => AssetReference(albumId, AssetType.Album)
    }

    val models = Seq(
      Some(stepOne.modelId),
      stepTwo.map(_.modelId)
    ).collect {
      case Some(modelId) => AssetReference(modelId, AssetType.CvModel)
    }

    val tables = Seq(
      stepOne.probabilityPredictionTableId,
      stepOne.testProbabilityPredictionTableId,
      stepTwo.flatMap(_.probabilityPredictionTableId),
      stepTwo.flatMap(_.testProbabilityPredictionTableId)
    ).collect {
      case Some(tableId) => AssetReference(tableId, AssetType.Table)
    }

    albums ++ models ++ tables
  }

}
