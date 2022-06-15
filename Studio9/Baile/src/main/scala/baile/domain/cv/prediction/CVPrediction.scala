package baile.domain.cv.prediction

import java.time.Instant
import java.util.UUID

import baile.domain.asset.Asset
import baile.domain.cv.EvaluateTimeSpentSummary
import baile.domain.cv.model.CVModelSummary

case class CVPrediction(
  ownerId: UUID,
  name: String,
  status: CVPredictionStatus,
  created: Instant,
  updated: Instant,
  description: Option[String],
  modelId: String,
  inputAlbumId: String,
  outputAlbumId: String,
  probabilityPredictionTableId: Option[String],
  evaluationSummary: Option[CVModelSummary],
  predictionTimeSpentSummary: Option[PredictionTimeSpentSummary],
  evaluateTimeSpentSummary: Option[EvaluateTimeSpentSummary],
  cvModelPredictOptions: Option[CVModelPredictOptions]
) extends Asset[CVPredictionStatus] {
  override val inLibrary: Boolean = true
}
