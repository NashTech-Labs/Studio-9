package baile.routes.contract.cv.model

import baile.domain.cv.EvaluateTimeSpentSummary
import baile.routes.contract.common.PipelineDetailsResponse
import play.api.libs.json.{ Json, OWrites }

case class CVEvaluationTimeSpentSummaryResponse(
  modelLoadingTime: Long,
  tasksQueuedTime: Long,
  totalJobTime: Long,
  dataLoadingTime: Long,
  scoreTime: Long,
  pipelineDetails: Seq[PipelineDetailsResponse]
)

object CVEvaluationTimeSpentSummaryResponse {

  implicit val EvaluationTimeSpentSummaryResponseWrites: OWrites[CVEvaluationTimeSpentSummaryResponse] =
    Json.writes[CVEvaluationTimeSpentSummaryResponse]

  def fromDomain(
    summary: EvaluateTimeSpentSummary
  ): CVEvaluationTimeSpentSummaryResponse =
    CVEvaluationTimeSpentSummaryResponse(
      modelLoadingTime = summary.loadModelTime,
      tasksQueuedTime = summary.tasksQueuedTime,
      totalJobTime = summary.totalJobTime,
      dataLoadingTime = summary.dataFetchTime,
      scoreTime = summary.scoreTime,
      pipelineDetails = summary.pipelineTimings.map(PipelineDetailsResponse.fromDomain)
    )

}
