package baile.domain.cv.result

import baile.domain.cv.model.{ CVModelSummary, CVModelTrainTimeSpentSummary }
import baile.domain.cv.{ AugmentationSummaryCell, EvaluateTimeSpentSummary }

case class CVTLTrainStepResult(
  modelId: String,
  outputAlbumId: Option[String],
  testOutputAlbumId: Option[String],
  autoAugmentationSampleAlbumId: Option[String],
  summary: Option[CVModelSummary],
  testSummary: Option[CVModelSummary],
  augmentationSummary: Option[Seq[AugmentationSummaryCell]],
  trainTimeSpentSummary: Option[CVModelTrainTimeSpentSummary],
  evaluateTimeSpentSummary: Option[EvaluateTimeSpentSummary],
  probabilityPredictionTableId: Option[String],
  testProbabilityPredictionTableId: Option[String]
)
