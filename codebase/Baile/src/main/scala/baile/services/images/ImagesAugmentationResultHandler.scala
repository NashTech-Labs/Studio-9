package baile.services.images

import java.util.UUID

import akka.event.LoggingAdapter
import baile.dao.images.AlbumDao
import baile.daocommons.WithId
import baile.domain.images._
import baile.domain.job.{ CortexJobStatus, CortexJobTerminalStatus }
import baile.services.cortex.job.{ CortexJobService, JobMetaService }
import baile.services.images.ImagesAugmentationResultHandler.Meta
import baile.services.images.ImagesCommonService.AugmentationResultImage
import baile.services.process.JobResultHandler
import baile.utils.TryExtensions._
import cortex.api.job.album.{ augmentation => CortexAugmentation }
import play.api.libs.json.{ Json, OFormat, Reads }

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

class ImagesAugmentationResultHandler(
  cortexJobService: CortexJobService,
  imagesCommonService: ImagesCommonService,
  jobMetaService: JobMetaService,
  albumDao: AlbumDao,
  logger: LoggingAdapter
) extends JobResultHandler[Meta] {

  override protected val metaReads: Reads[Meta] =
    ImagesAugmentationResultHandler.ImagesAugmentationResultHandlerMetaFormat

  override protected def handleResult(
    jobId: UUID,
    lastStatus: CortexJobTerminalStatus,
    meta: Meta
  )(implicit ec: ExecutionContext): Future[Unit] =
    lastStatus match {
      case CortexJobStatus.Completed =>
        for {
          album <- imagesCommonService.getAlbum(meta.outputAlbumId).map(_.getOrElse(
            throw new RuntimeException(
              s"Album ${ meta.outputAlbumId } was not found during result handling"
            )
          ))
          _ <- Try {
            if (album.entity.status != AlbumStatus.Saving) {
              throw new RuntimeException(
                s"Unexpected album status ${ album.entity.status } for album ${ album.id }. Expected it to be saving"
              )
            } else {
              ()
            }
          }.toFuture
          outputPath <- cortexJobService.getJobOutputPath(jobId)
          result <- jobMetaService.readRawMeta(jobId, outputPath).map(CortexAugmentation.AugmentationResult.parseFrom)
          jobTimeSummary <- cortexJobService.getJobTimeSummary(jobId)
          _ <- attachImagesToAlbum(meta, result, album.entity.labelMode)
          _ <- updateAlbumStatus(meta.outputAlbumId, AlbumStatus.Active)
          _ <- updateAugmentationTimeSpentSummary(
            meta.outputAlbumId,
            AugmentationTimeSpentSummary(
              dataFetchTime = result.dataFetchTime,
              augmentationTime = result.augmentationTime,
              tasksQueuedTime = jobTimeSummary.tasksQueuedTime,
              totalJobTime = jobTimeSummary.calculateTotalJobTime,
              pipelineTimings = cortexJobService.buildPipelineTimings(result.pipelineTimings)
            )
          )
        } yield ()
      case CortexJobStatus.Failed | CortexJobStatus.Cancelled =>
        logger.info("Job {} for album id: {}", lastStatus.toString, meta.outputAlbumId)
        handleException(meta)
    }

  override protected def handleException(meta: Meta): Future[Unit] =
    updateAlbumStatus(meta.outputAlbumId, AlbumStatus.Failed).map(_ => ())

  private def updateAlbumStatus(albumId: String, status: AlbumStatus): Future[Option[WithId[Album]]] =
    albumDao.update(albumId, _.copy(status = status))

  private def updateAugmentationTimeSpentSummary(
    albumId: String,
    summary: AugmentationTimeSpentSummary
  ): Future[Option[WithId[Album]]] =
    albumDao.update(albumId, _.copy(augmentationTimeSpentSummary = Some(summary)))

  private def attachImagesToAlbum(
    meta: Meta,
    augmentationResult: CortexAugmentation.AugmentationResult,
    outputAlbumLabelMode: AlbumLabelMode
  ): Future[Unit] = {
    val originalResultImages = if (meta.keepOriginalImages) {
      augmentationResult.originalImages.map { image =>
        AugmentationResultImage(image, None, Seq.empty[CortexAugmentation.AppliedAugmentation])
      }
    } else {
      Seq.empty
    }
    val augmentedResultImages = augmentationResult.augmentedImages.map { image =>
      AugmentationResultImage(image.getImage, Some(image.fileSize), image.augmentations)
    }
    val resultImages = originalResultImages ++ augmentedResultImages
    imagesCommonService.populateAugmentedAlbum(
      meta.inputAlbumId,
      meta.outputAlbumId,
      outputAlbumLabelMode,
      resultImages
    )
  }

}

object ImagesAugmentationResultHandler {

  case class Meta(outputAlbumId: String, inputAlbumId: String, keepOriginalImages: Boolean)

  implicit val ImagesAugmentationResultHandlerMetaFormat: OFormat[Meta] = Json.format[Meta]

}
