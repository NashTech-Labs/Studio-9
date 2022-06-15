package baile.routes.contract.images

import baile.domain.images.AugmentationTimeSpentSummary
import baile.routes.contract.common.PipelineDetailsResponse
import play.api.libs.json.{ Json, OWrites }

case class AugmentationTimeSpentSummaryResponse(
  augmentationTime: Long,
  tasksQueuedTime: Long,
  totalJobTime: Long,
  dataLoadingTime: Long,
  pipelineDetails: Seq[PipelineDetailsResponse]
)

object AugmentationTimeSpentSummaryResponse {

  implicit val AugmentationTimeSpentSummaryResponseWrites: OWrites[AugmentationTimeSpentSummaryResponse] =
    Json.writes[AugmentationTimeSpentSummaryResponse]

  def fromDomain(
    summary: AugmentationTimeSpentSummary
  ): AugmentationTimeSpentSummaryResponse =
    AugmentationTimeSpentSummaryResponse(
      augmentationTime = summary.augmentationTime,
      tasksQueuedTime = summary.tasksQueuedTime,
      totalJobTime = summary.totalJobTime,
      dataLoadingTime = summary.dataFetchTime,
      pipelineDetails = summary.pipelineTimings.map(PipelineDetailsResponse.fromDomain)
    )
  }
