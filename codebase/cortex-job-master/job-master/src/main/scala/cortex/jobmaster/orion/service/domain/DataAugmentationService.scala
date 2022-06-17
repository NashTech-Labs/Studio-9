package cortex.jobmaster.orion.service.domain

import cortex.api.job.JobRequest
import cortex.api.job.JobType.AlbumAugmentation
import cortex.api.job.album.augmentation._
import cortex.api.job.album.common.{ Tag, TagArea }
import cortex.jobmaster.jobs.TaskIdGenerator
import cortex.jobmaster.jobs.job.DataAugmentationJob
import cortex.jobmaster.jobs.time.JobTimeInfo
import cortex.jobmaster.modules.SettingsModule
import cortex.jobmaster.orion.service.domain.JobRequestPartialHandler.{ JobId, JobResult }
import cortex.scheduler.TaskScheduler
import cortex.task.StorageAccessParams.S3AccessParams
import cortex.task.data_augmentation.DataAugmentationParams

import scala.concurrent.{ ExecutionContext, Future }

class DataAugmentationService(
    dataAugmentationJob: DataAugmentationJob,
    s3AccessParams:      S3AccessParams
)(implicit val context: ExecutionContext) extends JobRequestPartialHandler
  with TaskIdGenerator with WithAugmentation {

  def augment(jobId: JobId, request: AugmentationRequest): Future[(AugmentationResult, JobTimeInfo)] = {

    val imgSplits = splitImages(request.images, request.augmentations.size, request.bloatFactor)

    val (augResults, augTypes) = imgSplits.zip(request.augmentations).map {
      case (imgSplit, augmentation) =>
        val localDAParams = toLocalDAParams(augmentation)
        val params = DataAugmentationParams.TransformParams(
          inputAlbumPath     = request.filePathPrefix,
          imagePaths         = imgSplit.map(_.getImage.filePath),
          referenceIds       = imgSplit.map(_.getImage.referenceId.getOrElse("")), // todo raise domain-level error
          tags               = imgSplit.map(i => i.tags.map(toLocalTag)),
          s3Params           = s3AccessParams,
          outputAlbumPath    = request.targetPrefix,
          augmentationParams = localDAParams
        )

        (dataAugmentationJob.transform(jobId, params), localDAParams.name.requestValue)
    }.unzip

    val mergedAugResults = Future.sequence(augResults) map { result =>
      val images = result.flatMap(augmentationResult => parseAugmentationResult(augmentationResult.transformImagesResult))
      val augmentationTime = result.map(_.augmentationEndTime).max - result.map(_.augmentationStartTime).min
      val dataFetchTime = result.map(_.dataFetchTime).sum
      val tasksTimeInfo = result.map(_.taskTimeInfo)
      val pipelineTimings = augTypes.zip(result.map(x => x.augmentationEndTime - x.augmentationStartTime)).toMap
      (images, augmentationTime, dataFetchTime, tasksTimeInfo, pipelineTimings)
    }

    if (!request.includeOriginalImages) {
      mergedAugResults.map {
        case (augmentedImages, augmentationTime, dataFetchTime, jobTasksTimeInfo, pipelineTimings) =>
          (AugmentationResult(
            originalImages   = Seq(),
            augmentedImages  = augmentedImages,
            augmentationTime = augmentationTime,
            dataFetchTime    = dataFetchTime,
            pipelineTimings  = pipelineTimings
          ), JobTimeInfo(jobTasksTimeInfo))
      }
    } else {
      val copiedImages = dataAugmentationJob.copyImages(
        jobId,
        DataAugmentationParams.ImagesCopyParams(
          inputAlbumPath  = request.filePathPrefix,
          imagePaths      = request.images.map(_.getImage.filePath),
          outputAlbumPath = request.targetPrefix,
          s3Params        = s3AccessParams
        )
      )
      for {
        result <- mergedAugResults
        (augmentedImages, augmentationTime, dataFetchTime, tasksTimeInfo, pipelineTimings) = result
        cr <- copiedImages
        updatedTasksTimeInfo = cr.taskTimeInfo +: tasksTimeInfo
      } yield (AugmentationResult(
        originalImages   =
          request.images.zip(cr.imagePaths).map {
            case (origImg, newImgPath) =>
              origImg.copy(image = Some(origImg.getImage.copy(filePath = newImgPath)))
          },
        augmentedImages  = augmentedImages,
        augmentationTime = augmentationTime,
        dataFetchTime    = dataFetchTime,
        pipelineTimings  = pipelineTimings
      ), JobTimeInfo(updatedTasksTimeInfo))
    }
  }

  private def splitImages[A](
    images:           Seq[A],
    numAugmentations: Int,
    bloatFactor:      Option[Int]
  ): List[Seq[A]] = {
    val bf = bloatFactor.getOrElse(1).min(numAugmentations) // to avoid duplications

    val bloatedImages = Seq.fill(bf)(images).flatten

    val imagesPerSplit = bloatedImages.size / numAugmentations
    val splits = bloatedImages.grouped(imagesPerSplit).toList

    // merge last two splits to make numAugmentations == splits.size
    val remainder = images.size - imagesPerSplit * numAugmentations
    if (remainder > 0 && splits.size > 1) {
      splits.dropRight(2) ++ List(splits.takeRight(2).flatten)
    } else {
      splits
    }
  }

  private def toLocalTag(t: Tag): DataAugmentationParams.Tag = {
    t.area match {
      case Some(TagArea(top, left, height, width)) => DataAugmentationParams.Tag(
        label = t.label,
        xMin  = Some(left),
        xMax  = Some(left + width),
        yMin  = Some(top),
        yMax  = Some(top + height)
      )
      case None => DataAugmentationParams.Tag(t.label)
    }
  }

  override def handlePartial: PartialFunction[(JobId, JobRequest), JobResult] = {
    case (jobId, jobReq) if jobReq.`type` == AlbumAugmentation =>
      val importRequest = AugmentationRequest.parseFrom(jobReq.payload.toByteArray)
      augment(jobId, importRequest)
  }

}

object DataAugmentationService {

  def apply(
    scheduler:      TaskScheduler,
    s3AccessParams: S3AccessParams,
    settings:       SettingsModule
  )(implicit executionContext: ExecutionContext): DataAugmentationService = {
    val job = new DataAugmentationJob(
      scheduler                 = scheduler,
      dataAugmentationJobConfig = settings.dataAugmentationConfig
    )
    new DataAugmentationService(job, s3AccessParams)
  }
}
