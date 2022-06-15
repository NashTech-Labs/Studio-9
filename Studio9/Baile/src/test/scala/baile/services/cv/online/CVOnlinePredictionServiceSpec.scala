package baile.services.cv.online

import java.time.Instant

import baile.BaseSpec
import baile.dao.images.{ AlbumDao, PictureDao }
import baile.daocommons.WithId
import baile.daocommons.filters.{ Filter, IdIs }
import baile.domain.images._
import baile.domain.usermanagement.User
import baile.services.cv.online.CVOnlinePredictionService.{ AlbumNotFoundError, AlbumNotForPredictionError }
import baile.services.usermanagement.util.TestData.SampleUser
import cortex.api.baile.PredictionResultItem
import org.mockito.ArgumentMatchers.{ any, argThat, eq => eqTo }
import org.mockito.Mockito._

import scala.concurrent.ExecutionContext

class CVOnlinePredictionServiceSpec extends BaseSpec {

  private val albumDao: AlbumDao = mock[AlbumDao]
  private val pictureDao: PictureDao = mock[PictureDao]

  implicit private val user: User = SampleUser

  private val albumId: String = randomString()
  private val nonPredictionAlbumId: String = randomString()

  private val album = Album(
    ownerId = user.id,
    name = randomString(),
    status = AlbumStatus.Active,
    `type` = AlbumType.Derived,
    labelMode = randomOf(AlbumLabelMode.Classification, AlbumLabelMode.Localization),
    created = Instant.now,
    updated = Instant.now,
    inLibrary = randomOf(true, false),
    picturesPrefix = randomString(),
    video = None,
    description = None,
    augmentationTimeSpentSummary = None
  )

  private val nonPredictionAlbum = album.copy(
    `type` = randomOf(AlbumType.Source, AlbumType.TrainResults),
  )

  private val service: CVOnlinePredictionService = new CVOnlinePredictionService(
    albumDao,
    pictureDao
  )

  when(albumDao.get(any[String])(any[ExecutionContext])).thenReturn(future(None))
  when(albumDao.get(any[Filter])(any[ExecutionContext])).thenReturn(future(None))
  when(albumDao.get(filterContains(IdIs(albumId)))(any[ExecutionContext]))
    .thenReturn(future(Some(WithId(album, albumId))))
  when(albumDao.get(filterContains(IdIs(nonPredictionAlbumId)))(any[ExecutionContext]))
    .thenReturn(future(Some(WithId(nonPredictionAlbum, nonPredictionAlbumId))))
  when(albumDao.update(any[String], any[Album => Album].apply)(any[ExecutionContext]))
    .thenReturn(future(None))
  when(albumDao.update(eqTo(albumId), any[Album => Album].apply)(any[ExecutionContext]))
    .thenReturn(future(Some(WithId(album, albumId))))


  "CVOnlinePredictionService#insertPredictedPictures" should {
    "insert pictures based on input" in {
      when(pictureDao.createMany(any[Seq[Picture]])(any[ExecutionContext])).thenReturn(future(Seq(randomString())))

      val predictions = Range(0, randomInt(10)).map(_ => predictionResult())

      whenReady(service.insertPredictedPictures(albumId, predictions)) { res =>
        res shouldBe Right(())
        verify(pictureDao).createMany(argThat[Seq[Picture]](_.length == predictions.length))(any[ExecutionContext])
      }
    }

    "fail on non prediction album" in {
      whenReady(service.insertPredictedPictures(nonPredictionAlbumId, Seq(predictionResult()))) { res =>
        res shouldBe Left(AlbumNotForPredictionError(nonPredictionAlbumId))
      }
    }

    "fail on unknown album" in {
      val randomAlbumId = randomString()

      whenReady(service.insertPredictedPictures(randomAlbumId, Seq(predictionResult()))) { res =>
        res shouldBe Left(AlbumNotFoundError(randomAlbumId))
      }
    }
  }

  private def predictionResult() = PredictionResultItem(
    filePath = randomPath("png"),
    fileSize = randomInt(100, 102400),
    fileName = randomString(),
    metadata = Map.empty,
    label = randomString(),
    confidence = randomInt(100)/100.0
  )

}
