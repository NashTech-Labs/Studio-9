package baile.domain.cv

import baile.domain.job.PipelineTiming

case class EvaluateTimeSpentSummary(
  dataFetchTime: Long,
  loadModelTime: Long,
  scoreTime: Long,
  tasksQueuedTime: Long,
  totalJobTime: Long,
  pipelineTimings: List[PipelineTiming]
)
