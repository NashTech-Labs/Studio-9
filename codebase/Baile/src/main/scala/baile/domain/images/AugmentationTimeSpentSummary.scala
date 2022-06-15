package baile.domain.images

import baile.domain.job.PipelineTiming

case class AugmentationTimeSpentSummary(
  dataFetchTime: Long,
  augmentationTime: Long,
  tasksQueuedTime: Long,
  totalJobTime: Long,
  pipelineTimings: List[PipelineTiming]
)
