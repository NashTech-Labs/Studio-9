package cortex.jobmaster.orion.service.domain

import cortex.api.job.JobRequest
import cortex.api.job.JobType.S3ImagesImport
import cortex.api.job.album.common.Tag
import cortex.api.job.album.uploading._
import cortex.common.future.FutureExtensions._
import cortex.common.logging.JMLoggerFactory
import cortex.jobmaster.jobs.job.image_uploading.{ ImageFile, ImageUploadingJob }
import cortex.jobmaster.modules.SettingsModule
import cortex.jobmaster.orion.service.domain.JobRequestPartialHandler.{ JobId, JobResult }
import cortex.scheduler.TaskScheduler
import cortex.task.StorageAccessParams.S3AccessParams
import cortex.api.job.common.{ FailedFile, File }
import cortex.task.image_uploading.ImageUploadingModule
import cortex.jobmaster.jobs.time.JobTimeInfo

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

class ImageUploadingService(imageUploadingJob: ImageUploadingJob)(implicit executionContext: ExecutionContext)
  extends JobRequestPartialHandler with ImageUploadingBase {

  def uploadImages(jobId: JobId, req: S3ImagesImportRequest): Future[(S3ImagesImportResult, JobTimeInfo)] = {
    def prepareImageUploadingParams() = Try {
      val s3AccessParams = S3AccessParams(
        bucket       = req.bucketName,
        accessKey    = req.awsAccessKey,
        secretKey    = req.awsSecretKey,
        region       = req.awsRegion,
        sessionToken = Some(req.awsSessionToken)
      )
      val imageFiles = if (req.images.nonEmpty) {
        Some(req.images.map(image => ImageFile(
          image.getBaseImage.filePath,
          image.fileSize,
          image.getBaseImage.referenceId
        )))
      } else {
        None
      }

      prepareParams(
        s3AccessParams          = s3AccessParams,
        targetPrefix            = req.targetPrefix,
        labelsCSVPath           = req.labelsCsvPath,
        labelsCSVFile           = req.labelsCsvFile.toByteArray,
        imagesPath              = Some(req.imagesPath),
        imageFiles              = imageFiles,
        applyLogTransformations = req.applyLogTransformation
      )
    }
    val uploadResult: Future[(S3ImagesImportResult, JobTimeInfo)] = for {
      params <- prepareImageUploadingParams().toFuture
      (imageUploadResult, jobTasksTimeInfo) <- imageUploadingJob.uploadImages(jobId, params)
    } yield {
      val succeed = imageUploadResult.succeed.map(image => {
        UploadedImage(
          file        = Some(File(image.path, image.size, image.name)),
          tags        = image.labels.map(Tag(_, None)),
          metadata    = image.meta,
          referenceId = image.referenceId
        )
      })
      val failed = imageUploadResult.failed.map(f => FailedFile(f.path, Some(f.reason)))
      (S3ImagesImportResult(succeed, failed), JobTimeInfo(jobTasksTimeInfo))
    }

    uploadResult
  }

  override def handlePartial: PartialFunction[(JobId, JobRequest), JobResult] = {
    case (jobId, jobReq) if jobReq.`type` == S3ImagesImport =>
      val importRequest = S3ImagesImportRequest.parseFrom(jobReq.payload.toByteArray)
      uploadImages(jobId, importRequest)
  }
}

object ImageUploadingService {

  def apply(
    scheduler:      TaskScheduler,
    s3AccessParams: S3AccessParams,
    settings:       SettingsModule
  )(implicit executionContext: ExecutionContext, loggerFactory: JMLoggerFactory): ImageUploadingService = {
    val imageUploadingModule = new ImageUploadingModule

    val imageUploadingJob = new ImageUploadingJob(
      scheduler            = scheduler,
      imageUploadingModule = imageUploadingModule,
      imageUploadingConfig = settings.imageUploadingConfig,
      outputS3AccessParams = s3AccessParams
    )

    new ImageUploadingService(imageUploadingJob)
  }
}
