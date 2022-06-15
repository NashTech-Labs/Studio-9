package baile.domain.tabular.result

import baile.domain.asset.{ AssetReference, AssetType }
import baile.domain.experiment.result.ExperimentResult
import baile.domain.tabular.model.TabularModelClass
import baile.domain.tabular.model.summary.{ PredictorSummary, TabularModelEvaluationSummary, TabularModelTrainSummary }

case class TabularTrainResult(
  modelId: String,
  outputTableId: String,
  holdOutOutputTableId: Option[String],
  outOfTimeOutputTableId: Option[String],
  predictedColumnName: String,
  classes: Option[Seq[TabularModelClass]],
  summary: Option[TabularModelTrainSummary],
  holdOutSummary: Option[TabularModelEvaluationSummary],
  outOfTimeSummary: Option[TabularModelEvaluationSummary],
  predictorsSummary: Seq[PredictorSummary]
) extends ExperimentResult {

  override def getAssetReferences: Seq[AssetReference] =
    Seq(
      Some(outputTableId),
      holdOutOutputTableId,
      outOfTimeOutputTableId
    ).collect {
      case Some(tableId) => AssetReference(tableId, AssetType.Table)
    } ++ Seq(AssetReference(modelId, AssetType.TabularModel))

}
