package baile.domain.cv.prediction

import baile.domain.job.PipelineTiming

case class PredictionTimeSpentSummary(
  dataFetchTime: Long,
  loadModelTime: Long,
  predictionTime: Long,
  tasksQueuedTime: Long,
  totalJobTime: Long,
  pipelineTimings: List[PipelineTiming]
)
