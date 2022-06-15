package baile.services.cv.online

import akka.event.LoggingAdapter
import baile.dao.images.{ AlbumDao, PictureDao }
import baile.daocommons.WithId
import baile.daocommons.filters.IdIs
import baile.domain.images.{ Album, AlbumType, Picture, PictureTag }
import baile.services.cv.online.CVOnlinePredictionService._
import cats.data.EitherT
import cats.implicits._
import cortex.api.baile.PredictionResultItem

import scala.concurrent.{ ExecutionContext, Future }

class CVOnlinePredictionService(
  protected val albumDao: AlbumDao,
  protected val pictureDao: PictureDao
)(implicit val ec: ExecutionContext, val logger: LoggingAdapter) {

  def insertPredictedPictures(
    albumId: String,
    results: Seq[PredictionResultItem]
  ): Future[Either[CVOnlinePredictionServiceError, Unit]] = {

    def preparePictures(album: WithId[Album]): Future[Seq[Picture]] = Future {
      results.map { row =>
        Picture(
          albumId = album.id,
          filePath = row.filePath,
          fileName = row.fileName,
          fileSize = Some(row.fileSize),
          caption = None,
          predictedCaption = None,
          tags = Seq.empty,
          predictedTags = Seq(PictureTag(
            label = row.label,
            area = None,
            confidence = Some(row.confidence)
          )),
          meta = row.metadata,
          originalPictureId = None,
          appliedAugmentations = None
        )
      }
    }

    def insertPictures(pictures: Seq[Picture]): Future[Unit] =
      pictureDao.createMany(pictures).map(_ => ())

    val result = for {
      album <- EitherT(loadPredictionAlbumMandatory(albumId))
      pictures <- EitherT.right[CVOnlinePredictionServiceError](preparePictures(album))
      _ <- EitherT.right[CVOnlinePredictionServiceError](insertPictures(pictures))
    } yield ()

    result.value
  }

  private def loadPredictionAlbumMandatory(
    albumId: String
  ): Future[Either[CVOnlinePredictionServiceError, WithId[Album]]] = {
    val result = for {
      album <- EitherT.fromOptionF(
        albumDao.get(IdIs(albumId)),
        AlbumNotFoundError(albumId): CVOnlinePredictionServiceError
      )
      predictionAlbum <- EitherT.cond[Future](
        album.entity.`type` == AlbumType.Derived,
        album,
        AlbumNotForPredictionError(albumId): CVOnlinePredictionServiceError
      )
    } yield predictionAlbum

    result.value
  }
}

object CVOnlinePredictionService {
  sealed trait CVOnlinePredictionServiceError

  case class AlbumNotFoundError(albumId: String) extends CVOnlinePredictionServiceError
  case class AlbumNotForPredictionError(albumId: String) extends CVOnlinePredictionServiceError
}
