package baile.services.images

import java.io._
import java.nio.file.Paths
import java.time.Instant
import java.util.UUID

import akka.stream.scaladsl.FileIO
import awscala.s3.S3Client
import baile.BaseSpec
import baile.dao.images.PictureDao.{ AlbumIdIs, MetaUpdateInfo }
import baile.dao.images.PictureDao
import baile.daocommons.WithId
import baile.daocommons.filters.Filter
import baile.domain.asset.AssetType
import baile.domain.common.S3Bucket
import baile.domain.images._
import baile.domain.process.{ Process, ProcessStatus, ResultHandlerMeta }
import baile.domain.usermanagement.User
import baile.services.common.S3BucketService
import baile.services.common.S3BucketService.BucketNotFound
import baile.services.cortex.job.CortexJobService
import baile.services.cortex.job.SupportedCortexJobTypes._
import baile.services.images.AlbumService.AlbumServiceError
import baile.services.images.ImagesUploadService.ImagesUploadServiceError._
import baile.services.images.PictureService.PictureServiceError
import baile.services.process.ProcessService
import baile.services.usermanagement.util.TestData.SampleUser
import cats.implicits._
import com.amazonaws.services.s3.model.S3ObjectInputStream
import cortex.api.job.album.uploading.{ S3ImagesImportRequest, S3VideoImportRequest }
import org.apache.http.client.methods.HttpGet
import org.mockito.ArgumentMatchers.{ any, eq => eqTo }
import org.mockito.Mockito._
import play.api.libs.json.{ JsObject, OWrites }

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.Try

class ImagesUploadServiceSpec extends BaseSpec {
  private val s3BucketService: S3BucketService = mock[S3BucketService]
  private val s3Client: S3Client = mock[S3Client](RETURNS_DEEP_STUBS)
  private val albumService: AlbumService = mock[AlbumService]
  private val pictureService: PictureService = mock[PictureService]
  private val pictureDao: PictureDao = mock[PictureDao]
  private val imagesCommonService: ImagesCommonService = mock[ImagesCommonService]
  private val cortexJobService: CortexJobService = mock[CortexJobService]
  private val processService: ProcessService = mock[ProcessService]

  private val albumId: String = randomString()
  private val albumInUseId: String = randomString()
  private val classificationAlbumId: String = s"{$albumId}_c"
  private val localizationAlbumId: String = s"{$albumId}_l"
  private val videoAlbumId: String = s"{$albumId}_video"
  private val nonActiveAlbumId: String = s"{$albumId}_wtf"
  private val nonEmptyAlbumId: String = s"{$albumId}_full"
  private val bucketId: String = randomString()

  implicit private val user: User = SampleUser

  private val album = Album(
    ownerId = user.id,
    name = randomString(),
    status = AlbumStatus.Active,
    `type` = AlbumType.Source,
    labelMode = randomOf(AlbumLabelMode.Classification, AlbumLabelMode.Localization),
    created = Instant.now,
    updated = Instant.now,
    inLibrary = randomOf(true, false),
    picturesPrefix = randomString(),
    video = None,
    description = None,
    augmentationTimeSpentSummary = None
  )

  private val classificationAlbum = album.copy(
    labelMode = AlbumLabelMode.Classification
  )

  private val localizationAlbum = album.copy(
    labelMode = AlbumLabelMode.Localization
  )

  private val videoAlbum = album.copy(
    video = Some(Video(
      filePath = randomPath("mp4"),
      fileSize = randomInt(1024, 102400),
      fileName = randomString(),
      frameRate = 60,
      frameCaptureRate = 5,
      height = randomInt(240, 1080),
      width = randomInt(320, 1980)
    ))
  )

  private val albumInUse = WithId(album, albumInUseId)

  private val nonActiveAlbum = album.copy(
    status = randomOf(AlbumStatus.Uploading, AlbumStatus.Failed, AlbumStatus.Saving)
  )

  private val onComplete: ResultHandlerMeta = ResultHandlerMeta(
    handlerClassName = "MyClassName",
    meta = JsObject(Seq.empty)
  )
  when(albumService.get(eqTo(albumId))(eqTo(user)))
    .thenReturn(future(Right(WithId(album, albumId))))
  when(albumService.get(eqTo(s"unknown_$albumId"))(eqTo(user)))
    .thenReturn(future(Left(AlbumServiceError.AlbumNotFound)))
  when(albumService.get(eqTo(albumInUseId))(eqTo(user)))
    .thenReturn(future(Right(albumInUse)))
  when(albumService.get(eqTo(classificationAlbumId))(eqTo(user)))
    .thenReturn(future(Right(WithId(classificationAlbum, classificationAlbumId))))
  when(albumService.get(eqTo(localizationAlbumId))(eqTo(user)))
    .thenReturn(future(Right(WithId(localizationAlbum, localizationAlbumId))))
  when(albumService.get(eqTo(videoAlbumId))(eqTo(user)))
    .thenReturn(future(Right(WithId(videoAlbum, videoAlbumId))))
  when(albumService.get(eqTo(nonActiveAlbumId))(eqTo(user)))
    .thenReturn(future(Right(WithId(nonActiveAlbum, nonActiveAlbumId))))
  when(albumService.get(eqTo(nonEmptyAlbumId))(eqTo(user)))
    .thenReturn(future(Right(WithId(album, nonEmptyAlbumId))))

  when(pictureService.ensurePictureOperationAvailable(any[WithId[Album]], any[User]))
    .thenReturn(future(().asRight))

  when(albumService.update(any[String], any[AlbumStatus], any[Boolean]))
    .thenReturn(future(None))
  when(albumService.update(eqTo(albumId), any[AlbumStatus], any[Boolean]))
    .thenReturn(future(Some(WithId(album, albumId))))

  when(pictureDao.get(any[String])(any[ExecutionContext])).thenReturn(future(None))
  when(pictureDao.count(any[Filter])(any[ExecutionContext])).thenReturn(future(0))
  when(pictureDao.count(filterContains(AlbumIdIs(nonEmptyAlbumId)))(any[ExecutionContext])).thenReturn(future(10))
  when(pictureDao.updateMeta(eqTo(albumId), any[Seq[MetaUpdateInfo]])(any[ExecutionContext]))
    .thenReturn(future(randomInt(10)))
  when(pictureDao.updateMeta(eqTo(classificationAlbumId), any[Seq[MetaUpdateInfo]])(any[ExecutionContext]))
    .thenReturn(future(randomInt(10)))

  when(s3BucketService.dereferenceBucket(any[S3Bucket])).thenReturn(future(BucketNotFound.asLeft))
  when(s3BucketService.dereferenceBucket(eqTo(S3Bucket.IdReference(bucketId))))
    .thenReturn(future(S3Bucket.AccessOptions(
      region = randomOf("us-east-1", "us-east-2"),
      bucketName = randomString(),
      accessKey = Some(randomString(20)),
      secretKey = Some(randomString(32)),
      sessionToken = Some(randomString(32))
    ).asRight))
  when(s3BucketService.prepareS3Client(any[S3Bucket.AccessOptions])).thenReturn(Try(s3Client))
  when(s3Client.getObject(any[String], any[String]).getObjectContent).thenReturn(new S3ObjectInputStream(
    new ByteArrayInputStream(randomString(1024).getBytes),
    new HttpGet()
  ))
  when(cortexJobService.submitJob(
    any[S3ImagesImportRequest],
    any[UUID]
  )(eqTo(implicitly[SupportedCortexJobType[S3ImagesImportRequest]]))).thenReturn(future(UUID.randomUUID))
  when(cortexJobService.submitJob(
    any[S3VideoImportRequest],
    any[UUID]
  )(eqTo(implicitly[SupportedCortexJobType[S3VideoImportRequest]]))).thenReturn(future(UUID.randomUUID))
  when(processService.startProcess(
    any[UUID],
    eqTo(albumId),
    eqTo(AssetType.Album),
    any[Class[ImagesImportFromS3ResultHandler]],
    any[ImagesImportFromS3ResultHandler.Meta],
    any[UUID],
    any[Option[String]]
  )(any[OWrites[ImagesImportFromS3ResultHandler.Meta]])).thenReturn(future(WithId(Process(
    targetId = albumId,
    targetType = AssetType.Album,
    ownerId = user.id,
    authToken = None,
    jobId = UUID.randomUUID,
    status = ProcessStatus.Running,
    progress = Some(1),
    estimatedTimeRemaining = Some(1e5.seconds),
    created = Instant.now,
    started = None,
    completed = None,
    onComplete = onComplete,
    errorCauseMessage = None,
    errorDetails = None,
    auxiliaryOnComplete = Seq.empty
  ), "foo")))

  when(imagesCommonService.getImagesPathPrefix(any[Album])).thenReturn("path")

  private val service: ImagesUploadService = new ImagesUploadService(
    pictureDao,
    albumService,
    pictureService,
    imagesCommonService,
    cortexJobService,
    processService,
    s3BucketService,
    conf
  )


  "ImagesUploadServiceSpec#importImagesFromS3" should {

    "run import job" in {
      whenReady(service.importImagesFromS3(
        albumId,
        S3Bucket.IdReference(bucketId),
        randomPath(),
        Some(randomPath("csv")),
        None,
        None
      )) { result =>
        result shouldBe a [Right[_, _]]
        verify(cortexJobService, atLeastOnce).submitJob(
          any[S3ImagesImportRequest],
          any[UUID]
        )(eqTo(implicitly[SupportedCortexJobType[S3ImagesImportRequest]]))
      }
    }

    "fail when bucket is unknown" in {
      whenReady(service.importImagesFromS3(
        albumId,
        S3Bucket.IdReference(s"unknown_$bucketId"),
        randomPath(),
        Some(randomPath("csv")),
        None,
        None
      )) { result =>
        result shouldBe a [Left[BucketError.type, _]]
      }
    }

    "fail when album is unknown" in {
      whenReady(service.importImagesFromS3(
        s"unknown_$albumId",
        S3Bucket.IdReference(bucketId),
        randomPath(),
        Some(randomPath("csv")),
        None,
        None
      )) { result =>
        result shouldBe a [Left[AlbumNotFoundError, _]]
      }
    }

    "fail when album has video" in {
      when(pictureService.ensurePictureOperationAvailable(eqTo(WithId(videoAlbum, videoAlbumId)), any[User]))
        .thenReturn(future(PictureServiceError.PictureOperationUnavailable("album contains video").asLeft))
      whenReady(service.importImagesFromS3(
        videoAlbumId,
        S3Bucket.IdReference(bucketId),
        randomPath(),
        Some(randomPath("csv")),
        None,
        None
      )) { result =>
        result shouldBe a [Left[PictureOperationUnavailable, _]]
      }
    }

    "fail when album is not active" in {
      when(pictureService.ensurePictureOperationAvailable(eqTo(WithId(videoAlbum, videoAlbumId)), any[User]))
        .thenReturn(future(PictureServiceError.PictureOperationUnavailable("album is not active").asLeft))
      whenReady(service.importImagesFromS3(
        videoAlbumId,
        S3Bucket.IdReference(bucketId),
        randomPath(),
        Some(randomPath("csv")),
        None,
        None
      )) { result =>
        result shouldBe a [Left[PictureOperationUnavailable, _]]
      }
    }

    "fail when album is in use" in {
      when(pictureService.ensurePictureOperationAvailable(eqTo(albumInUse), any[User]))
        .thenReturn(future(PictureServiceError.PictureOperationUnavailable("album is in use").asLeft))
      whenReady(service.importImagesFromS3(
        albumInUseId,
        S3Bucket.IdReference(bucketId),
        randomPath(),
        Some(randomPath("csv")),
        None,
        None
      )) { result =>
        result shouldBe a [Left[PictureOperationUnavailable, _]]
      }
    }

  }

  "ImagesUploadServiceSpec#importVideoFromS3" should {

    "run import job" in {
      whenReady(service.importVideoFromS3(
        albumId,
        S3Bucket.IdReference(bucketId),
        randomPath("mp4"),
        Some(randomInt(1, 10))
      )) { result =>
        result shouldBe a [Right[_, _]]
        verify(cortexJobService, atLeastOnce).submitJob(
          any[S3VideoImportRequest],
          any[UUID]
        )(eqTo(implicitly[SupportedCortexJobType[S3VideoImportRequest]]))
      }
    }

    "fail when bucket is unknown" in {
      whenReady(service.importVideoFromS3(
        albumId,
        S3Bucket.IdReference(s"unknown_$bucketId"),
        randomPath("mp4"),
        Some(randomInt(1, 10))
      )) { result =>
        result shouldBe a [Left[BucketError.type, _]]
      }
    }

    "fail when album is unknown" in {
      whenReady(service.importVideoFromS3(
        s"unknown_$albumId",
        S3Bucket.IdReference(bucketId),
        randomPath("mp4"),
        Some(randomInt(1, 10))
      )) { result =>
        result shouldBe a [Left[AlbumNotFoundError, _]]
      }
    }

    "fail when album has video" in {
      when(pictureService.ensurePictureOperationAvailable(eqTo(WithId(videoAlbum, videoAlbumId)), any[User]))
        .thenReturn(future(PictureServiceError.PictureOperationUnavailable("album contains video").asLeft))
      whenReady(service.importVideoFromS3(
        videoAlbumId,
        S3Bucket.IdReference(bucketId),
        randomPath("mp4"),
        Some(randomInt(1, 10))
      )) { result =>
        result shouldBe a [Left[PictureOperationUnavailable, _]]
      }
    }

    "fail when album is not active" in {
      when(pictureService.ensurePictureOperationAvailable(eqTo(WithId(videoAlbum, videoAlbumId)), any[User]))
        .thenReturn(future(PictureServiceError.PictureOperationUnavailable("album is not active").asLeft))
      whenReady(service.importVideoFromS3(
        videoAlbumId,
        S3Bucket.IdReference(bucketId),
        randomPath("mp4"),
        Some(randomInt(1, 10))
      )) { result =>
        result shouldBe a [Left[PictureOperationUnavailable, _]]
      }
    }

    "fail when album has pictures" in {
      whenReady(service.importVideoFromS3(
        nonEmptyAlbumId,
        S3Bucket.IdReference(bucketId),
        randomPath("mp4"),
        Some(randomInt(1, 10))
      )) { result =>
        result shouldBe a [Left[PictureOperationUnavailable, _]]
      }
    }

    "fail when album is in use" in {
      when(pictureService.ensurePictureOperationAvailable(eqTo(albumInUse), any[User]))
        .thenReturn(future(PictureServiceError.PictureOperationUnavailable("album is in use").asLeft))
      whenReady(service.importVideoFromS3(
        albumInUseId,
        S3Bucket.IdReference(bucketId),
        randomPath("mp4"),
        Some(randomInt(1, 10))
      )) { result =>
        result shouldBe a [Left[PictureOperationUnavailable, _]]
      }
    }

  }

  "ImagesUploadServiceSpec#importLabelsFromCSVFileS3" should {

    // TODO find a way to mock S3.download nicely and enable this test
    "import labels" ignore {
      whenReady(service.importLabelsFromCSVFileS3(
        classificationAlbumId,
        S3Bucket.IdReference(bucketId),
        randomPath("csv")
      )) { result =>
        result shouldBe a [Right[_, _]]
      }
    }

    "fail when bucket is unknown" in {
      whenReady(service.importLabelsFromCSVFileS3(
        albumId,
        S3Bucket.IdReference(s"unknown_$bucketId"),
        randomPath("csv")
      )) { result =>
        result shouldBe a [Left[BucketError.type, _]]
      }
    }

    "fail when album is unknown" in {
      whenReady(service.importLabelsFromCSVFileS3(
        s"unknown_$albumId",
        S3Bucket.IdReference(bucketId),
        randomPath("csv")
      )) { result =>
        result shouldBe a [Left[AlbumNotFoundError, _]]
      }
    }

    "fail when album is in use" in {
      when(pictureService.ensurePictureOperationAvailable(eqTo(albumInUse), any[User]))
        .thenReturn(future(PictureServiceError.PictureOperationUnavailable("album is in use").asLeft))
      whenReady(service.importLabelsFromCSVFileS3(
        albumInUseId,
        S3Bucket.IdReference(bucketId),
        randomPath("csv")
      )) { result =>
        result shouldBe a [Left[PictureOperationUnavailable, _]]
      }
    }

  }

  "ImagesUploadServiceSpec#importLabelsFromCSVFile" should {

    "import labels" in {
      whenReady(service.importLabelsFromCSVFile(
        classificationAlbumId,
        FileIO.fromPath(Paths.get(getClass.getResource("/test.csv").getPath))
      )) { result =>
        result shouldBe a [Right[_, _]]
      }
    }

    "fail when album is unknown" in {
      whenReady(service.importLabelsFromCSVFile(
        s"unknown_$albumId",
        FileIO.fromPath(Paths.get(getClass.getResource("/test.csv").getPath))
      )) { result =>
        result shouldBe a [Left[AlbumNotFoundError, _]]
      }
    }

    "fail when album is in use" in {
      when(pictureService.ensurePictureOperationAvailable(eqTo(albumInUse), any[User]))
        .thenReturn(future(PictureServiceError.PictureOperationUnavailable("album is in use").asLeft))
      whenReady(service.importLabelsFromCSVFile(
        albumInUseId,
        FileIO.fromPath(Paths.get(getClass.getResource("/test.csv").getPath))
      )) { result =>
        result shouldBe a [Left[PictureOperationUnavailable, _]]
      }
    }

  }
}
