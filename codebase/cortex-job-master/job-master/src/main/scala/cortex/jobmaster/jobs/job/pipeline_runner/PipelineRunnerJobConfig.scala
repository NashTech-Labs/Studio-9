package cortex.jobmaster.jobs.job.pipeline_runner

case class PipelineRunnerJobConfig(
    cpus:            Double,
    taskMemoryLimit: Double,
    gpus:            Int
)
