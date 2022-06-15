package cortex.jobmaster.orion.service.domain
import cortex.api.job.JobRequest
import cortex.api.job.JobType.S3VideoImport
import cortex.api.job.common.File
import cortex.api.job.album.uploading.{ S3VideoImportRequest, S3VideoImportResult }
import cortex.jobmaster.jobs.job.video_uploading.VideoUploadingJob
import cortex.jobmaster.jobs.time.JobTimeInfo
import cortex.jobmaster.modules.SettingsModule
import cortex.jobmaster.orion.service.domain.JobRequestPartialHandler.{ JobId, JobResult }
import cortex.scheduler.TaskScheduler
import cortex.task.StorageAccessParams.S3AccessParams
import cortex.task.video_uploading.VideoUploadingModule
import cortex.task.video_uploading.VideoUploadingParams.VideoImportTaskParams

import scala.concurrent.{ ExecutionContext, Future }

class VideoUploadingService(job: VideoUploadingJob)(implicit executionContext: ExecutionContext)
  extends JobRequestPartialHandler {

  def upload(jobId: JobId, uploadRequest: S3VideoImportRequest): Future[(S3VideoImportResult, JobTimeInfo)] = {

    val outputAlbumPath = uploadRequest.targetPrefix

    val uploadParams = VideoImportTaskParams(
      inputS3Params    = S3AccessParams(
        uploadRequest.bucketName,
        uploadRequest.awsAccessKey,
        uploadRequest.awsSecretKey,
        uploadRequest.awsRegion
      ),
      outputS3Params   = job.outputS3AccessParams,
      videoPath        = uploadRequest.videoPath,
      frameCaptureRate = uploadRequest.frameCaptureRate,
      albumPath        = outputAlbumPath,
      blockSize        = job.videoUploadingJobConfig.blockSize
    )

    for {
      uploadResult <- job.uploadVideo(jobId, uploadParams)
    } yield {
      (S3VideoImportResult(
        uploadResult.importedFrames.map(frame =>
          File(
            filePath = frame.fileName, // path, relative to targetPrefix
            fileName = frame.fileName,
            fileSize = frame.fileSize
          )),
        Some(File(
          filePath = uploadResult.videoFilePath, // relative to targetPrefix
          fileName = uploadResult.videoFileName,
          fileSize = uploadResult.videoSize
        )),
        uploadResult.videoFrameRate.toInt,
        uploadResult.videoHeight,
        uploadResult.videoWidth
      ), JobTimeInfo(Seq(uploadResult.taskTimeInfo)))
    }
  }

  override def handlePartial: PartialFunction[(JobId, JobRequest), JobResult] = {

    case (jobId, jobReq) if jobReq.`type` == S3VideoImport =>
      val uploadRequest = S3VideoImportRequest.parseFrom(jobReq.payload.toByteArray)
      this.upload(jobId, uploadRequest)
  }
}

object VideoUploadingService {

  def apply(
    scheduler:      TaskScheduler,
    s3AccessParams: S3AccessParams,
    settings:       SettingsModule
  )(implicit executionContext: ExecutionContext): VideoUploadingService = {
    val videoUploadingJob = new VideoUploadingJob(
      scheduler               = scheduler,
      module                  = new VideoUploadingModule(),
      videoUploadingJobConfig = settings.videoUploadingConfig,
      outputS3AccessParams    = s3AccessParams
    )

    new VideoUploadingService(videoUploadingJob)
  }
}
