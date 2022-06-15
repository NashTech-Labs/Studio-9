package baile.routes.contract.tabular.model.summary

import baile.domain.tabular.model.summary._
import play.api.libs.functional.syntax._
import play.api.libs.json._

case class TabularModelSummaryResponse(
  evaluationSummaryResponse: TabularModelEvaluationSummaryResponse,
  predictors: Option[Seq[PredictorSummaryResponse]],
  roc: Option[Seq[(Double, Double)]],
  areaUnderROC: Option[Double]
)

object TabularModelSummaryResponse {

  implicit val TabularModelSummaryResponseWrites: OWrites[TabularModelSummaryResponse] = (
    __.write[TabularModelEvaluationSummaryResponse] ~
    (__ \ "roc").writeNullable[Seq[(Double, Double)]] ~
    (__ \ "areaUnderROC").writeNullable[Double] ~
    (__ \ "predictors").writeNullable[Seq[PredictorSummaryResponse]]
  )((summary: TabularModelSummaryResponse) => (
    summary.evaluationSummaryResponse,
    summary.roc,
    summary.areaUnderROC,
    summary.predictors
  ))

  private def buildEvaluationSummaryResponse(
    summary: TabularModelEvaluationSummary
  ): TabularModelEvaluationSummaryResponse = summary match {
    case summary: RegressionSummary =>
      LinearModelEvaluationSummaryResponse(
        rmse = summary.rmse,
        r2 = summary.r2,
        MAPE = summary.mape
      )
    case summary: ClassificationSummary =>
      LogisticModelEvaluationSummaryResponse(
        KS = None,
        confusionMatrix = buildConfusionMatrix(summary.confusionMatrix)
      )
    case summary: BinaryClassificationEvaluationSummary =>
      LogisticModelEvaluationSummaryResponse(
        KS = Some(summary.ks),
        confusionMatrix = buildConfusionMatrix(summary.classificationSummary.confusionMatrix)
      )
  }

  private def buildEvaluationSummaryResponse(
    summary: BinaryClassificationTrainSummary
  ): TabularModelEvaluationSummaryResponse =
    buildEvaluationSummaryResponse(summary.evaluationSummary)

  private def buildConfusionMatrix(matrix: Seq[ClassConfusion]): Seq[ConfusionMatrixRowResponse] =
    matrix.map(ConfusionMatrixRowResponse.fromDomain)

  private def fromRegressionSummary(
    summary: RegressionSummary,
    predictorsSummaryResponse: Option[Seq[PredictorSummaryResponse]]
  ): TabularModelSummaryResponse =
    TabularModelSummaryResponse(
      evaluationSummaryResponse = buildEvaluationSummaryResponse(summary),
      predictors = predictorsSummaryResponse,
      roc = None,
      areaUnderROC = None
    )

  private def fromClassificationSummary(
    summary: ClassificationSummary,
    predictorsSummaryResponse: Option[Seq[PredictorSummaryResponse]]
  ): TabularModelSummaryResponse =
    TabularModelSummaryResponse(
      evaluationSummaryResponse = buildEvaluationSummaryResponse(summary),
      predictors = predictorsSummaryResponse,
      roc = None,
      areaUnderROC = None
    )

  private def fromBinaryClassificationTrainSummary(
    summary: BinaryClassificationTrainSummary,
    predictorsSummaryResponse: Option[Seq[PredictorSummaryResponse]]
  ): TabularModelSummaryResponse =
    TabularModelSummaryResponse(
      evaluationSummaryResponse = buildEvaluationSummaryResponse(summary),
      predictors = predictorsSummaryResponse,
      roc = Some(summary.rocValues.map { rocValue => (rocValue.falsePositive, rocValue.truePositive) }),
      areaUnderROC = Some(summary.areaUnderROC)
    )

  private def fromBinaryClassificationEvaluationSummary(
    summary: BinaryClassificationEvaluationSummary,
    predictorsSummaryResponse: Option[Seq[PredictorSummaryResponse]]
  ): TabularModelSummaryResponse =
    TabularModelSummaryResponse(
      evaluationSummaryResponse = buildEvaluationSummaryResponse(summary),
      predictors = predictorsSummaryResponse,
      roc = None,
      areaUnderROC = None
    )

  private def buildPredictorsSummaryResponse(
    predictorsSummary: Seq[PredictorSummary]
  ): Option[Seq[PredictorSummaryResponse]] =
    if (predictorsSummary.isEmpty) None
    else Some(predictorsSummary.map(PredictorSummaryResponse.fromDomain))

  def fromDomain(
    summary: TabularModelTrainSummary,
    predictorsSummary: Seq[PredictorSummary]
  ): TabularModelSummaryResponse = {
    val predictorsSummaryResponse = buildPredictorsSummaryResponse(predictorsSummary)
    summary match {
      case summary: RegressionSummary =>
        fromRegressionSummary(summary, predictorsSummaryResponse)
      case summary: ClassificationSummary =>
        fromClassificationSummary(summary, predictorsSummaryResponse)
      case summary: BinaryClassificationTrainSummary =>
        fromBinaryClassificationTrainSummary(summary, predictorsSummaryResponse)
    }
  }

  def fromDomain(
    summary: TabularModelEvaluationSummary,
    predictorsSummary: Seq[PredictorSummary]
  ): TabularModelSummaryResponse = {
    val predictorsSummaryResponse = buildPredictorsSummaryResponse(predictorsSummary)
    summary match {
      case summary: RegressionSummary =>
        fromRegressionSummary(summary, predictorsSummaryResponse)
      case summary: ClassificationSummary =>
        fromClassificationSummary(summary, predictorsSummaryResponse)
      case summary: BinaryClassificationEvaluationSummary =>
        fromBinaryClassificationEvaluationSummary(summary, predictorsSummaryResponse)
    }
  }

}
