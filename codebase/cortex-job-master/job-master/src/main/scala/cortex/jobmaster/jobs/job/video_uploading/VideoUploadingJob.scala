package cortex.jobmaster.jobs.job.video_uploading

import cortex.jobmaster.jobs.TaskIdGenerator
import cortex.scheduler.TaskScheduler
import cortex.task.StorageAccessParams.S3AccessParams
import cortex.task.video_uploading.VideoUploadingModule
import cortex.task.video_uploading.VideoUploadingParams._

import scala.concurrent.{ ExecutionContext, Future }

class VideoUploadingJob(
    scheduler:                   TaskScheduler,
    module:                      VideoUploadingModule,
    val videoUploadingJobConfig: VideoUploadingJobConfig,
    val outputS3AccessParams:    S3AccessParams
)(implicit val context: ExecutionContext) extends TaskIdGenerator {

  def uploadVideo(jobId: String, params: VideoImportTaskParams): Future[VideoImportTaskResult] = {
    val taskPath = s"$jobId/video_import"
    val taskId = genTaskId(jobId)
    val task = module.transformTask(
      id       = taskId,
      jobId    = jobId,
      taskPath = taskPath,
      params   = params,
      cpus     = videoUploadingJobConfig.cpus,
      memory   = videoUploadingJobConfig.taskMemoryLimit
    )
    scheduler.submitTask(task)
  }
}
