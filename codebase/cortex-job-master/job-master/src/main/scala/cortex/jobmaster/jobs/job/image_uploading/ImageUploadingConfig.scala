package cortex.jobmaster.jobs.job.image_uploading

/**
 *
 * @param blockSize hdf5 block size
 * @param additionalTaskSize additional size per task in megabytes
 * @param maxTaskMemSize upload task max size in megabytes
 * @param parallelizationFactor approximate total amount of tasks while image upload process
 * @param minGroupSize minimal image group size per task
 * @param imageMaxSize maximal single image size in megabytes
 */
case class ImageUploadingConfig(
    blockSize:             Int,
    additionalTaskSize:    Double,
    maxTaskMemSize:        Double,
    parallelizationFactor: Int,
    minGroupSize:          Int,
    imageMaxSize:          Double,
    cpus:                  Double
)
