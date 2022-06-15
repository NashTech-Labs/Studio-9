package baile.services.images

import java.util.UUID

import akka.event.LoggingAdapter
import baile.dao.images.PictureDao
import baile.daocommons.WithId
import baile.domain.images.Picture
import baile.domain.job.{ CortexJobStatus, CortexJobTerminalStatus }
import baile.services.cortex.job.{ CortexJobService, JobMetaService }
import baile.services.images.MergeAlbumsResultHandler._
import baile.services.process.JobResultHandler
import baile.utils.TryExtensions._
import cats.implicits._
import com.typesafe.config.Config
import cortex.api.job.album.uploading.{ S3ImagesImportResult, UploadedImage }
import play.api.libs.json.{ Json, OFormat, Reads }

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

class MergeAlbumsResultHandler(
  protected val conf: Config,
  cortexJobService: CortexJobService,
  jobMetaService: JobMetaService,
  commonService: ImagesCommonService,
  pictureDao: PictureDao,
  logger: LoggingAdapter
) extends JobResultHandler[Meta] {

  override protected val metaReads: Reads[Meta] =
    MergeAlbumsResultHandler.MergeAlbumsResultHandlerMetaFormat

  override protected def handleResult(
    jobId: UUID,
    lastStatus: CortexJobTerminalStatus,
    meta: Meta
  )(implicit ec: ExecutionContext): Future[Unit] = {

    // TODO Abstract ImagesCommonService#populateAugmentedAlbum and this methods code
    def populateMergedAlbum(
      inputAlbumIds: Seq[String],
      onlyLabelled: Boolean,
      outputAlbumId: String,
      uploadedImages: Seq[UploadedImage]
    ): Future[Unit] = {

      val imagesProcessingBatchSize: Int = conf.getInt("album.images-job-result-processing.batch-size")

      def buildUploadedPicturesBatch(
        inputPicturesBatch: Seq[WithId[Picture]],
        uploadedImagesBatch: Seq[UploadedImage]
      ): Try[Seq[Picture]] = {

        def getInputPicture(
          pictureId: String
        ): Try[WithId[Picture]] = Try {
          inputPicturesBatch
            .find(_.id == pictureId)
            .getOrElse(throw new RuntimeException(
              s"Unexpectedly not found input picture $pictureId for result image"
            ))
        }

        uploadedImagesBatch.toList.foldM(Seq.empty[Picture]) {
          case (builtPictures, uploadedImage) => getInputPicture(uploadedImage.getReferenceId) map {
            case WithId(inputPicture, _) =>
              builtPictures :+ Picture(
                albumId = outputAlbumId,
                filePath = uploadedImage.getFile.filePath,
                fileName = inputPicture.fileName,
                fileSize = Some(uploadedImage.getFile.fileSize),
                tags = inputPicture.tags,
                caption = inputPicture.caption,
                meta = inputPicture.meta,
                predictedCaption = None,
                predictedTags = Seq.empty,
                originalPictureId = None,
                appliedAugmentations = None
              )
          }
        }
      }

      uploadedImages.grouped(imagesProcessingBatchSize).toList.foldM(()) { (_, uploadedImagesBatch) =>
        for {
          inputPicturesBatch <- commonService.getPictures(
            inputAlbumIds,
            onlyLabelled,
            uploadedImagesBatch.map(_.getReferenceId)
          )
          resultPicturesBatch <- buildUploadedPicturesBatch(inputPicturesBatch, uploadedImagesBatch).toFuture
          _ <- commonService.attachPictures(outputAlbumId, resultPicturesBatch)
        } yield ()
      }
    }

    lastStatus match {
      case CortexJobStatus.Completed =>
        for {
          outputPath <- cortexJobService.getJobOutputPath(jobId)
          uploadResult <- jobMetaService.readRawMeta(jobId, outputPath).map(S3ImagesImportResult.parseFrom)
          _ <- populateMergedAlbum(
            meta.inputAlbumsIds,
            meta.onlyLabelled,
            meta.albumId,
            uploadResult.images
          )
          _ <- commonService.makeAlbumActive(meta.albumId)
        } yield ()
      case CortexJobStatus.Failed | CortexJobStatus.Cancelled =>
        logger.info("Upload job {} for album id: {}", lastStatus.toString, meta.albumId)
        handleException(meta)
    }
  }

  override protected def handleException(meta: Meta): Future[Unit] =
    commonService.makeAlbumActive(meta.albumId).map(_ => ())

}

object MergeAlbumsResultHandler {

  case class Meta(
    albumId: String,
    inputAlbumsIds: Seq[String],
    onlyLabelled: Boolean
  )

  implicit def MergeAlbumsResultHandlerMetaFormat: OFormat[Meta] = Json.format[Meta]
}
