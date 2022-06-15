package baile.routes.contract.cv.model

import baile.domain.cv.model.CVModelTrainTimeSpentSummary
import baile.routes.contract.common.PipelineDetailsResponse
import play.api.libs.json.{ Json, OWrites }

case class CVModelTrainTimeSpentSummaryResponse(
  dataLoadingTime: Long,
  trainingTime: Long,
  modelSavingTime: Long,
  initialPredictionTime: Long,
  tasksQueuedTime: Long,
  totalJobTime: Long,
  pipelineDetails: Seq[PipelineDetailsResponse]
)

object CVModelTrainTimeSpentSummaryResponse {

  def fromDomain(
    summary: CVModelTrainTimeSpentSummary
  ): CVModelTrainTimeSpentSummaryResponse =
    CVModelTrainTimeSpentSummaryResponse(
      dataLoadingTime = summary.dataFetchTime,
      trainingTime = summary.trainingTime,
      modelSavingTime = summary.saveModelTime,
      initialPredictionTime = summary.predictionTime,
      tasksQueuedTime = summary.tasksQueuedTime,
      totalJobTime = summary.totalJobTime,
      pipelineDetails = summary.pipelineTimings.map(PipelineDetailsResponse.fromDomain)
    )

  implicit val CVModelTrainTimeSpentSummaryResponseWrites: OWrites[CVModelTrainTimeSpentSummaryResponse] =
    Json.writes[CVModelTrainTimeSpentSummaryResponse]

}
