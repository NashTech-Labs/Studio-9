package baile.services.images

import java.util.UUID

import akka.event.LoggingAdapter
import baile.dao.images.{ AlbumDao, PictureDao }
import baile.domain.images.{ AlbumStatus, Picture, Video }
import baile.domain.job.{ CortexJobStatus, CortexJobTerminalStatus }
import baile.services.cortex.job.{ CortexJobService, JobMetaService }
import baile.services.images.VideoImportFromS3ResultHandler.Meta
import baile.services.process.JobResultHandler
import cortex.api.job.album.uploading.S3VideoImportResult
import play.api.libs.json.{ Json, OFormat, Reads }

import scala.concurrent.{ ExecutionContext, Future }

class VideoImportFromS3ResultHandler(
  cortexJobService: CortexJobService,
  jobMetaService: JobMetaService,
  imagesCommonService: ImagesCommonService,
  albums: AlbumDao,
  pictures: PictureDao,
  logger: LoggingAdapter
) extends JobResultHandler[Meta] {

  override protected val metaReads: Reads[Meta] =
    VideoImportFromS3ResultHandler.VideoImportFromS3ResultHandlerMetaFormat

  override protected def handleResult(
    jobId: UUID,
    lastStatus: CortexJobTerminalStatus,
    meta: Meta
  )(implicit ec: ExecutionContext): Future[Unit] = {

    def insertPictures(uploadResult: S3VideoImportResult): Future[Unit] = {
      val newPictures = uploadResult.imageFiles.map { file =>
        Picture(
          albumId = meta.albumId,
          filePath = file.filePath,
          fileName = file.fileName,
          fileSize = Some(file.fileSize),
          tags = Seq.empty,
          meta = Map.empty,
          caption = None,
          predictedCaption = None,
          predictedTags = Seq.empty,
          originalPictureId = None,
          appliedAugmentations = None
        )
      }

      pictures.createMany(newPictures).map(_ => ())
    }

    def updateAlbum(uploadResult: S3VideoImportResult): Future[Unit] = {
      val videoFile = uploadResult.getVideoFile

      albums.update(meta.albumId, _.copy(
        status = AlbumStatus.Active,
        video = Some(Video(
          filePath = videoFile.filePath,
          fileName = videoFile.fileName,
          fileSize = videoFile.fileSize,
          frameRate = uploadResult.videoFrameRate,
          frameCaptureRate = meta.frameRateDivider,
          height = uploadResult.videoHeight,
          width = uploadResult.videoWidth
        ))
      )).map(_ => ())
    }

    lastStatus match {
      case CortexJobStatus.Completed =>
        for {
          outputPath <- cortexJobService.getJobOutputPath(jobId)
          uploadResult <- jobMetaService.readRawMeta(jobId, outputPath).map(S3VideoImportResult.parseFrom)
          _ <- insertPictures(uploadResult)
          _ <- updateAlbum(uploadResult)
        } yield ()
      case CortexJobStatus.Failed | CortexJobStatus.Cancelled =>
        logger.info("Video upload job {} for album id: {}", lastStatus.toString, meta.albumId)
        imagesCommonService.makeAlbumActive(meta.albumId).map(_ => ())
    }
  }

  override protected def handleException(meta: Meta): Future[Unit] =
    imagesCommonService.makeAlbumActive(meta.albumId).map(_ => ())

}

object VideoImportFromS3ResultHandler {
  case class Meta(
    albumId: String,
    frameRateDivider: Int
  )

  implicit val VideoImportFromS3ResultHandlerMetaFormat: OFormat[Meta] = Json.format[Meta]
}

