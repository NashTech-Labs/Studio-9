package baile.domain.tabular.pipeline

import baile.domain.asset.{ AssetReference, AssetType }
import baile.domain.experiment.pipeline.ExperimentPipeline
import baile.domain.tabular.model.ModelColumn

case class TabularTrainPipeline(
  samplingWeightColumnName: Option[String],
  predictorColumns: List[ModelColumn],
  responseColumn: ModelColumn,
  inputTableId: String,
  holdOutInputTableId: Option[String],
  outOfTimeInputTableId: Option[String]
) extends ExperimentPipeline {

  override def getAssetReferences: Seq[AssetReference] =
    Seq(
      Some(inputTableId),
      holdOutInputTableId,
      outOfTimeInputTableId
    ).collect {
      case Some(tableId) => AssetReference(tableId, AssetType.Table)
    }

}
