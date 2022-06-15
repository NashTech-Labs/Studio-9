package baile.routes.contract.cv.prediction

import java.time.Instant

import baile.daocommons.WithId
import baile.domain.cv.prediction.{ CVPrediction, CVPredictionStatus }
import play.api.libs.json.{ Json, OWrites }
import baile.routes.contract.cv.PredictionStatusFormat
import baile.routes.contract.cv.model.{ CVEvaluationTimeSpentSummaryResponse, CVModelSummaryResponse }

case class CVPredictionResponse(
  id: String,
  ownerId: String,
  name: String,
  status: CVPredictionStatus,
  created: Instant,
  updated: Instant,
  modelId: String,
  input: String,
  output: String,
  probabilityPredictionTableId: Option[String],
  summary: Option[CVModelSummaryResponse],
  description: Option[String],
  predictionTimeSpentSummary: Option[CVPredictionTimeSpentSummaryResponse],
  evaluationTimeSpentSummary: Option[CVEvaluationTimeSpentSummaryResponse]
)

object CVPredictionResponse {

  implicit val CVPredictionResponseWrites: OWrites[CVPredictionResponse] = Json.writes[CVPredictionResponse]

  def fromDomain(prediction: WithId[CVPrediction]): CVPredictionResponse = {
    CVPredictionResponse(
      id = prediction.id,
      ownerId = prediction.entity.ownerId.toString,
      name = prediction.entity.name,
      status = prediction.entity.status,
      created = prediction.entity.created,
      updated = prediction.entity.updated,
      modelId = prediction.entity.modelId,
      input = prediction.entity.inputAlbumId,
      output = prediction.entity.outputAlbumId,
      probabilityPredictionTableId = prediction.entity.probabilityPredictionTableId,
      summary = prediction.entity.evaluationSummary.map(CVModelSummaryResponse.fromDomain),
      description = prediction.entity.description,
      predictionTimeSpentSummary = prediction.entity.predictionTimeSpentSummary
        .map(CVPredictionTimeSpentSummaryResponse.fromDomain),
      evaluationTimeSpentSummary = prediction.entity.evaluateTimeSpentSummary
        .map(CVEvaluationTimeSpentSummaryResponse.fromDomain)
    )
  }

}
