package cortex.jobmaster.jobs.job.video_uploading

case class VideoUploadingJobConfig(
    cpus:            Double,
    taskMemoryLimit: Double,
    blockSize:       Int
)
