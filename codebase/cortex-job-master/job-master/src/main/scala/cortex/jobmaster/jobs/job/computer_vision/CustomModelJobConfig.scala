package cortex.jobmaster.jobs.job.computer_vision

case class CustomModelJobConfig(
    cpus:            Double,
    taskMemoryLimit: Double,
    gpus:            Int
)
