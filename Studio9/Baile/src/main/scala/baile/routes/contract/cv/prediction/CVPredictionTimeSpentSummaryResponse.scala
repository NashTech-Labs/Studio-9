package baile.routes.contract.cv.prediction

import baile.domain.cv.prediction.PredictionTimeSpentSummary
import baile.routes.contract.common.PipelineDetailsResponse
import play.api.libs.json.{ Json, OWrites }

case class CVPredictionTimeSpentSummaryResponse(
  dataLoadingTime: Long,
  predictionTime: Long,
  modelLoadingTime: Long,
  tasksQueuedTime: Long,
  totalJobTime: Long,
  pipelineDetails: Seq[PipelineDetailsResponse]
)

object CVPredictionTimeSpentSummaryResponse {

  implicit val CVPredictionTimeSpentSummaryResponseWrites: OWrites[CVPredictionTimeSpentSummaryResponse] =
    Json.writes[CVPredictionTimeSpentSummaryResponse]

  def fromDomain(
    summary: PredictionTimeSpentSummary
  ): CVPredictionTimeSpentSummaryResponse =
    CVPredictionTimeSpentSummaryResponse(
      dataLoadingTime = summary.dataFetchTime,
      predictionTime = summary.predictionTime,
      modelLoadingTime = summary.loadModelTime,
      tasksQueuedTime = summary.tasksQueuedTime,
      totalJobTime = summary.totalJobTime,
      pipelineDetails = summary.pipelineTimings.map(PipelineDetailsResponse.fromDomain)
    )

}
