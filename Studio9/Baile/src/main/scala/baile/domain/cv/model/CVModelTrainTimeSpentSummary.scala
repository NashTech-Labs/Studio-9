package baile.domain.cv.model

import baile.domain.job.PipelineTiming

case class CVModelTrainTimeSpentSummary(
  dataFetchTime: Long,
  trainingTime: Long,
  saveModelTime: Long,
  predictionTime: Long,
  tasksQueuedTime: Long,
  totalJobTime: Long,
  pipelineTimings: List[PipelineTiming]
)
