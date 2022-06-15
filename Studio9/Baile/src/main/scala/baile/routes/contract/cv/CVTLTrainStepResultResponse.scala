package baile.routes.contract.cv

import baile.domain.cv.result.CVTLTrainStepResult
import baile.routes.contract.cv.model._
import play.api.libs.json.{ Json, OWrites }

case class CVTLTrainStepResultResponse(
  cvModelId: String,
  output: Option[String],
  testOutput: Option[String],
  augmentedSampleAlbum: Option[String],
  summary: Option[CVModelSummaryResponse],
  testSummary: Option[CVModelSummaryResponse],
  augmentationSummary: Option[Seq[AlbumAugmentationSummaryResponse]],
  trainTimeSpentSummary: Option[CVModelTrainTimeSpentSummaryResponse],
  evaluationTimeSpentSummary: Option[CVEvaluationTimeSpentSummaryResponse],
  probabilityPredictionTableId: Option[String],
  testProbabilityPredictionTableId: Option[String]
)

object CVTLTrainStepResultResponse {

  def fromDomain(in: CVTLTrainStepResult): CVTLTrainStepResultResponse = CVTLTrainStepResultResponse(
    cvModelId = in.modelId,
    output = in.outputAlbumId,
    testOutput = in.testOutputAlbumId,
    augmentedSampleAlbum = in.autoAugmentationSampleAlbumId,
    summary = in.summary.map(CVModelSummaryResponse.fromDomain),
    testSummary = in.testSummary.map(CVModelSummaryResponse.fromDomain),
    augmentationSummary = in.augmentationSummary.map(_.map(AlbumAugmentationSummaryResponse.fromDomain)),
    trainTimeSpentSummary = in.trainTimeSpentSummary.map(CVModelTrainTimeSpentSummaryResponse.fromDomain),
    evaluationTimeSpentSummary = in.evaluateTimeSpentSummary.map(CVEvaluationTimeSpentSummaryResponse.fromDomain),
    probabilityPredictionTableId = in.probabilityPredictionTableId,
    testProbabilityPredictionTableId = in.testProbabilityPredictionTableId
  )

  implicit val CVTLTrainStepOneParamsResponseWrites: OWrites[CVTLTrainStepResultResponse] =
    Json.writes[CVTLTrainStepResultResponse]

}
