package cortex.jobmaster.jobs.job.computer_vision

case class LocalizationJobConfig(
    cpus:                        Double,
    taskMemoryLimit:             Double,
    gpus:                        Int,
    featureExtractorTaskGpus:    Int,
    composeVideoTaskMemoryLimit: Double
)
