package cortex.jobmaster.jobs.job.dataset

/**
 *
 * @param parallelizationFactor approximate total amount of tasks while file transfer process
 * @param minGroupSize minimal file group size per task
 * @param fileMaxSize maximal single file size in megabytes
 * @param cpus number of cpu per a task
 * @param memory amount of memory per a task
 */
case class DatasetTransferConfig(
    parallelizationFactor: Int    = 10,
    minGroupSize:          Int    = 10,
    fileMaxSize:           Double = 4096.0,
    cpus:                  Double = 2.0,
    memory:                Double = 512
)
