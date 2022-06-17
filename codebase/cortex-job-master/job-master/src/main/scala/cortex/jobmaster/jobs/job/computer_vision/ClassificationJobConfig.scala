package cortex.jobmaster.jobs.job.computer_vision

case class ClassificationJobConfig(
    cpus:            Double,
    taskMemoryLimit: Double,
    gpus:            Int
)
