package baile.services.images

import java.util.UUID

import akka.event.LoggingAdapter
import baile.dao.images.PictureDao
import baile.daocommons.WithId
import baile.domain.images.{ Album, Picture, PictureTag }
import baile.domain.job.{ CortexJobStatus, CortexJobTerminalStatus }
import baile.services.cortex.job.{ CortexJobService, JobMetaService }
import baile.services.images.ImagesImportFromS3ResultHandler.Meta
import baile.services.process.JobResultHandler
import baile.utils.TryExtensions._
import cortex.api.job.album.common.Tag
import cortex.api.job.album.uploading.S3ImagesImportResult
import play.api.libs.json.{ Json, OFormat, Reads }

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

class ImagesImportFromS3ResultHandler(
  cortexJobService: CortexJobService,
  imagesCommonService: ImagesCommonService,
  jobMetaService: JobMetaService,
  pictureDao: PictureDao,
  logger: LoggingAdapter
) extends JobResultHandler[Meta] {

  override protected val metaReads: Reads[Meta] =
    ImagesImportFromS3ResultHandler.ImagesImportFromS3ResultHandlerMetaFormat

  override protected def handleResult(
    jobId: UUID,
    lastStatus: CortexJobTerminalStatus,
    meta: Meta
  )(implicit ec: ExecutionContext): Future[Unit] = {

    def loadAlbum(): Future[WithId[Album]] = imagesCommonService.getAlbum(meta.albumId).map(_.getOrElse(
      throw new RuntimeException(s"Uploading album ${ meta.albumId } was not found during upload result handling")
    ))

    def insertPictures(uploadResult: S3ImagesImportResult, album: WithId[Album]): Future[Unit] = {

      def convertCortexTags(tags: Seq[Tag]): Try[Seq[PictureTag]] =
        Try.sequence(tags.map(tag => imagesCommonService.convertCortexTagToPictureTag(tag, album.entity.labelMode)))

      def buildPictures: Try[Seq[Picture]] = Try.sequence {
        uploadResult.images.map { image =>
          convertCortexTags(image.tags).map { tags =>
            Picture(
              albumId = album.id,
              filePath = image.getFile.filePath,
              fileName = image.getFile.fileName,
              fileSize = Some(image.getFile.fileSize),
              tags = tags,
              meta = image.metadata,
              caption = None,
              predictedCaption = None,
              predictedTags = Seq.empty,
              originalPictureId = None,
              appliedAugmentations = None
            )
          }
        }
      }

      for {
        pictures <- buildPictures.toFuture
        _ <- pictureDao.createMany(pictures)
      } yield ()
    }

    lastStatus match {
      case CortexJobStatus.Completed =>
        for {
          album <- loadAlbum()
          outputPath <- cortexJobService.getJobOutputPath(jobId)
          uploadResult <- jobMetaService.readRawMeta(jobId, outputPath).map(S3ImagesImportResult.parseFrom)
          _ <- insertPictures(uploadResult, album)
          _ <- imagesCommonService.makeAlbumActive(meta.albumId)
        } yield ()
      case CortexJobStatus.Failed | CortexJobStatus.Cancelled =>
        logger.info("Upload job {} for album id: {}", lastStatus.toString, meta.albumId)
        handleException(meta)
    }
  }

  override protected def handleException(meta: Meta): Future[Unit] =
    imagesCommonService.makeAlbumActive(meta.albumId).map(_ => ())

}

object ImagesImportFromS3ResultHandler {
  case class Meta(
    albumId: String
  )

  implicit val ImagesImportFromS3ResultHandlerMetaFormat: OFormat[Meta] = Json.format[Meta]
}
