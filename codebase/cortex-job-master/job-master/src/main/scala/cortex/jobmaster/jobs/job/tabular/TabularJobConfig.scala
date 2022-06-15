package cortex.jobmaster.jobs.job.tabular

case class TabularJobConfig(
    cpus:            Double,
    taskMemoryLimit: Double,
    kFolds:          Int,
    numHPSamples:    Int
)
