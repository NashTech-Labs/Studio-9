package cortex.jobmaster.jobs.job.computer_vision

case class AutoencoderJobConfig(
    cpus:            Double,
    taskMemoryLimit: Double,
    gpus:            Int
)
