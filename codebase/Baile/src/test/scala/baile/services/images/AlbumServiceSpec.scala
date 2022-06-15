package baile.services.images

import java.time.Instant
import java.util.UUID

import baile.BaseSpec
import baile.dao.asset.Filters.NameIs
import baile.dao.images.AlbumDao
import baile.daocommons.WithId
import baile.daocommons.filters.IdIs
import baile.domain.asset.{ AssetReference, AssetType }
import baile.domain.common.S3Bucket
import baile.domain.images.AlbumLabelMode.{ Classification, Localization }
import baile.domain.images.AlbumStatus.Active
import baile.domain.images.AlbumType.Source
import baile.domain.images._
import baile.domain.images.augmentation._
import baile.domain.process.{ Process, ProcessStatus, ResultHandlerMeta }
import baile.domain.remotestorage.S3TemporaryCredentials
import baile.domain.usermanagement.User
import baile.services.asset.sharing.AssetSharingService
import baile.services.cortex.job.CortexJobService
import baile.services.cortex.job.SupportedCortexJobTypes.SupportedCortexJobType
import baile.services.images.AlbumService.AlbumServiceError.AlbumNotFound
import baile.services.images.AlbumService.{ AlbumNameValidationError, AlbumServiceCreateError, AlbumServiceError }
import baile.services.process.ProcessService
import baile.services.project.ProjectService
import baile.services.usermanagement.util.TestData.SampleUser
import cortex.api.job.album.augmentation.AugmentationRequest
import cortex.api.job.album.uploading.S3ImagesImportRequest
import org.mockito.ArgumentMatchers.{ any, argThat, eq => eqTo }
import org.mockito.Mockito.{ verify, when }
import play.api.libs.json.{ JsObject, OWrites }

import scala.concurrent.ExecutionContext

class AlbumServiceSpec extends BaseSpec {

  private val dao = mock[AlbumDao]
  private val pictureStorage = mock[PicturesS3StorageService]
  private val cortexJobService = mock[CortexJobService]
  private val processService = mock[ProcessService]
  private val imagesCommonService = mock[ImagesCommonService]
  private val projectService = mock[ProjectService]

  private val assetSharingService = mock[AssetSharingService]
  private val service = new AlbumService(
    imagesCommonService,
    dao,
    pictureStorage,
    cortexJobService,
    processService,
    assetSharingService,
    projectService
  )
  implicit private val user: User = SampleUser
  private val augmentationParams = Seq(
    RotationParams(Seq(1.0f, 2.0f), true, 1),
    ShearingParams(Seq(10, 30), true, 1),
    NoisingParams(Seq(1, 2), 1),
    ZoomInParams(Seq(2), true, 1),
    ZoomOutParams(Seq(0.2f), true, 1),
    OcclusionParams(Seq(0.1f), OcclusionMode.Zero, true, 32, 1),
    TranslationParams(Seq(0.1f), TranslationMode.Constant, true, 1),
    SaltPepperParams(Seq(0.1f), 0.5f, 1),
    PhotometricDistortParams(PhotometricDistortAlphaBounds(0.5f, 1.5f), 18, 1),
    CroppingParams(Seq(0.25f), 1, false, 1),
    BlurringParams(Seq(0.5f), 1)
  )
  private val invalidRotateAugmentationParams = Seq(
    RotationParams(Seq(300, 790), true, 1)
  )
  private val invalidShearingAugmentationParams = Seq(ShearingParams(Seq(300, 790), true, 1))
  private val invalidNoisingAugmentationParams = Seq(NoisingParams(Seq(-1), 1))

  private val dateTime: Instant = Instant.now()
  private val albumId = randomString()
  private val noAlbumId = randomString()
  private val forbiddenAlbumId = randomString()
  private val localizationAlbumId = randomString()
  private val emptyAlbumId = randomString()
  private val albumInProgressId = randomString()
  private val derivedAlbumId = randomString()
  private val accessKeyId = randomString()
  private val secretAccessKey = randomString()
  private val sessionToken = randomString()
  private val bucketRegion = randomString()
  private val bucketName = randomString()
  private val tempCredentials = S3TemporaryCredentials(
    region = bucketRegion,
    bucketName = bucketName,
    accessKey = accessKeyId,
    secretKey = secretAccessKey,
    sessionToken = sessionToken
  )
  private val pictureOne = Picture(
    albumId = albumId,
    filePath = randomPath(),
    fileName = randomString(),
    fileSize = Some(randomInt(1024, 102400)),
    tags = Seq(PictureTag(
      label = randomString()
    )),
    caption = None,
    predictedCaption = None,
    predictedTags = Seq.empty,
    meta = Map.empty,
    originalPictureId = None,
    appliedAugmentations = None
  )
  private val pictureTwo = Picture(
    albumId = albumId,
    filePath = randomPath(),
    fileName = randomString(),
    fileSize = Some(randomInt(1024, 102400)),
    tags = Seq(PictureTag(
      label = randomString()
    )),
    caption = None,
    predictedCaption = None,
    predictedTags = Seq.empty,
    meta = Map.empty,
    originalPictureId = None,
    appliedAugmentations = None
  )
  private val pictureThree = Picture(
    albumId = albumId,
    filePath = randomPath(),
    fileName = randomString(),
    fileSize = Some(randomInt(1024, 102400)),
    tags = Seq(PictureTag(
      label = randomString()
    )),
    caption = None,
    predictedCaption = None,
    predictedTags = Seq.empty,
    meta = Map.empty,
    originalPictureId = None,
    appliedAugmentations = None
  )

  private val album = WithId(Album(
    ownerId = user.id,
    name = "name",
    status = Active,
    `type` = Source,
    labelMode = Classification,
    inLibrary = true,
    created = dateTime,
    updated = dateTime,
    picturesPrefix = "albums/name",
    description = Some("description"),
    augmentationTimeSpentSummary = None
  ), albumId)
  private val emptyAlbum = album.copy(id = emptyAlbumId)
  private val forbiddenAlbum = WithId(album.entity.copy(
    ownerId = UUID.randomUUID
  ), forbiddenAlbumId)
  private val jobId = UUID.randomUUID
  private val localizationAlbum = WithId(album.entity.copy(
    labelMode = AlbumLabelMode.Localization
  ), localizationAlbumId)
  private val albumInProgress = WithId(album.entity.copy(
    status = randomOf(AlbumStatus.Uploading, AlbumStatus.Saving)
  ), albumInProgressId)
  private val derivedAlbum = WithId(album.entity.copy(
    `type` = randomOf(AlbumType.TrainResults, AlbumType.Derived)
  ), derivedAlbumId)

  when(dao.create(any[Album])(any[ExecutionContext])).thenReturn(future(albumId))
  when(dao.create(any[String => Album])(any[ExecutionContext])).thenReturn(future(album))
  when(dao.get(any[String])(any[ExecutionContext])).thenReturn(future(None))
  when(dao.get(eqTo(albumId))(any[ExecutionContext])).thenReturn(future(Some(album)))
  when(dao.get(eqTo(forbiddenAlbumId))(any[ExecutionContext])).thenReturn(future(Some(forbiddenAlbum)))
  when(dao.get(eqTo(localizationAlbumId))(any[ExecutionContext])).thenReturn(future(Some(localizationAlbum)))
  when(dao.get(eqTo(emptyAlbumId))(any[ExecutionContext])).thenReturn(future(Some(emptyAlbum)))
  when(dao.get(eqTo(albumInProgressId))(any[ExecutionContext])).thenReturn(future(Some(albumInProgress)))
  when(dao.get(eqTo(derivedAlbumId))(any[ExecutionContext])).thenReturn(future(Some(derivedAlbum)))
  when(dao.get(filterContains(IdIs(noAlbumId)))(any[ExecutionContext])).thenReturn(future(None))
  when(dao.get(filterContains(IdIs(emptyAlbumId)))(any[ExecutionContext])).thenReturn(future(Some(emptyAlbum)))
  when(dao.get(filterContains(IdIs(albumId)))(any[ExecutionContext])).thenReturn(future(Some(album)))
  when(dao.count(filterContains(NameIs("nameTaken")))(any[ExecutionContext])).thenReturn(future(1))
  when(dao.count(filterContains(NameIs("name")))(any[ExecutionContext])).thenReturn(future(0))
  when(dao.count(filterContains(NameIs("newName")))(any[ExecutionContext])).thenReturn(future(0))
  when(dao.update(any[String], any[Album => Album].apply)(any[ExecutionContext])).thenReturn(future(Some(album)))
  when(projectService.removeAssetFromAllProjects(any[AssetReference])(any[User])).thenReturn(future(()))

  when(pictureStorage.accessOptions).thenReturn(S3Bucket.AccessOptions(
    region = randomOf("us-east-1", "us-east-2"),
    bucketName = randomString(),
    accessKey = Some(randomString(20)),
    secretKey = Some(randomString(32)),
    sessionToken = Some(randomString(32))
  ))
  when(pictureStorage.path(any[String], any[Seq[String]]: _*)).thenReturn("path")
  when(pictureStorage.getTemporaryCredentials(eqTo("album-prefix"), any[User])(any[ExecutionContext]))
    .thenReturn (future(tempCredentials))
  when(imagesCommonService.storagePathPrefix).thenReturn("prefix")
  when(imagesCommonService.getImagesPathPrefix(any[Album])).thenReturn("album-prefix")

  when(imagesCommonService.getPictures(any[String], any[Boolean])).thenReturn(future(Seq.empty))
  when(imagesCommonService.getPictures(any[Seq[String]], any[Boolean])).thenReturn(future(Seq.empty))
  when(imagesCommonService.getPictures(argThat[Seq[String]](_.contains(albumId)), any[Boolean]))
    .thenReturn(future(Seq(WithId(
      entity = Picture(
        albumId = albumId,
        filePath = randomPath(),
        fileName = randomString(),
        fileSize = Some(randomInt(1024, 102400)),
        tags = Seq(PictureTag(
          label = randomString()
        )),
        caption = None,
        predictedCaption = None,
        predictedTags = Seq.empty,
        meta = Map.empty,
        originalPictureId = None,
        appliedAugmentations = None
      ),
      id = randomString()
    ))))
  when(imagesCommonService.getPictures(any[String], any[Boolean]))
    .thenReturn(future(Seq(
      WithId(entity = pictureOne, id = "1"),
      WithId(entity = pictureTwo, id = "2"),
      WithId(entity = pictureThree, id = "3")
    )))
  when(imagesCommonService.getPictures(any[String], eqTo(Seq("1", "2"))))
    .thenReturn(future(Seq(
      WithId(entity = pictureOne, id = "1"),
      WithId(entity = pictureTwo, id = "2"),
    )))
  when(imagesCommonService.countPictures(any[Seq[String]], any[Boolean])).thenReturn(future(0))
  when(imagesCommonService.countPictures(argThat[Seq[String]](_.contains(albumId)), any[Boolean]))
    .thenReturn(future(1))
  when(imagesCommonService.countPictures(any[String], any[Boolean])).thenReturn(future(0))
  when(imagesCommonService.countPictures(eqTo(albumId), any[Boolean]))
    .thenReturn(future(1))
  when(imagesCommonService.deletePictures(any[String])).thenReturn(future(()))
  when(imagesCommonService.attachPictures(any[String], eqTo(Seq(pictureOne, pictureTwo)))).thenReturn(future(()))
  when(imagesCommonService.attachPictures(
    any[String],
    eqTo(Seq(pictureOne, pictureTwo, pictureThree))
  )).thenReturn(future(()))
  when(cortexJobService.submitJob(
    any[S3ImagesImportRequest],
    any[UUID]
  )(eqTo(implicitly[SupportedCortexJobType[S3ImagesImportRequest]]))).thenReturn(future(jobId))
  when(cortexJobService.submitJob(
    any[AugmentationRequest],
    any[UUID]
  )(eqTo(implicitly[SupportedCortexJobType[AugmentationRequest]]))).thenReturn(future(jobId))
  when(processService.startProcess(
    eqTo(jobId),
    any[String],
    eqTo(AssetType.Album),
    any[Class[MergeAlbumsResultHandler]],
    any[MergeAlbumsResultHandler.Meta],
    any[UUID],
    any[Option[String]]
  )(any[OWrites[MergeAlbumsResultHandler.Meta]])).thenReturn(future(WithId(
    Process(
      targetId = albumId,
      targetType = AssetType.Album,
      ownerId = user.id,
      authToken = None,
      jobId = jobId,
      status = ProcessStatus.Running,
      progress = Some(0),
      estimatedTimeRemaining = None,
      created = dateTime,
      started = None,
      completed = None,
      errorCauseMessage = None,
      errorDetails = None,
      onComplete = ResultHandlerMeta(
        handlerClassName = MergeAlbumsResultHandler.getClass.getName,
        meta = JsObject.empty
      ),
      auxiliaryOnComplete = Seq.empty
    ),
    id = randomString()
  )))
  when(processService.cancelProcesses(any[String], eqTo(AssetType.Album))(eqTo(user)))
    .thenReturn(future(Right(())))
  when(assetSharingService.deleteSharesForAsset(any[String], eqTo(AssetType.Album))(eqTo(user)))
    .thenReturn(future(()))

  "AlbumService#create" should {

    "create Album" in {
      whenReady(service.create(Some("name"), Classification, None, Some("description"), None)) { result =>
        assert(result.isRight)
        assert(result.right.exists { albumResponse =>
          WithId(albumResponse.entity.copy(
            created = dateTime,
            updated = dateTime
          ), albumResponse.id) === album
        })
      }
    }

    "fail on name taken" in {
      whenReady(service.create(Some("nameTaken"), Classification, None, None, None)) { result =>
        result shouldBe Left(AlbumNameValidationError.AlbumNameTaken)
      }
    }

    "start upload if albums merging is requested" in {
      whenReady(service.create(
        Some("name"),
        Classification,
        Some(MergeAlbumsParams(
          inputAlbumsIds = Seq(albumId),
          onlyLabeledPictures = randomBoolean()
        )),
        None,
        None
      )) { result =>
        assert(result.isRight)
        assert(result.right.exists { albumResponse =>
          WithId(albumResponse.entity.copy(
            created = dateTime,
            updated = dateTime
          ), albumResponse.id) === album
        })
        verify(cortexJobService).submitJob(
          any[S3ImagesImportRequest],
          any[UUID]
        )(eqTo(implicitly[SupportedCortexJobType[S3ImagesImportRequest]]))
      }
    }

    "fail when album to merge is not found" in {
      whenReady(service.create(
        Some("name"),
        Classification,
        Some(MergeAlbumsParams(
          inputAlbumsIds = Seq(randomString()),
          onlyLabeledPictures = randomBoolean()
        )),
        None,
        None
      )) { result =>
        result shouldBe Left(AlbumServiceCreateError.AlbumsToMergeNotFound)
      }
    }

    "fail when album to merge is inaccessible" in {
      whenReady(service.create(
        Some("name"),
        Classification,
        Some(MergeAlbumsParams(
          inputAlbumsIds = Seq(forbiddenAlbumId),
          onlyLabeledPictures = randomBoolean()
        )),
        None,
        None
      )) { result =>
        result shouldBe Left(AlbumServiceCreateError.AlbumsToMergeNotFound)
      }
    }

    "fail when merging requested with empty albumIds" in {
      whenReady(service.create(
        Some("name"),
        Classification,
        Some(MergeAlbumsParams(
          inputAlbumsIds = Seq.empty,
          onlyLabeledPictures = randomBoolean()
        )),
        None,
        None
      )) { result =>
        result shouldBe Left(AlbumServiceCreateError.AlbumsToMergeEmpty)
      }
    }

    "fail when album to merge has incompatible label mode" in {
      whenReady(service.create(
        Some("name"),
        Classification,
        Some(MergeAlbumsParams(
          inputAlbumsIds = Seq(localizationAlbumId),
          onlyLabeledPictures = randomBoolean()
        )),
        None,
        None
      )) { result =>
        result shouldBe Left(AlbumServiceCreateError.AlbumsToMergeIncorrectLabelMode)
      }
    }

    "fail when album to merge has no pictures" in {
      whenReady(service.create(
        Some("name"),
        Classification,
        Some(MergeAlbumsParams(
          inputAlbumsIds = Seq(emptyAlbumId),
          onlyLabeledPictures = randomBoolean()
        )),
        None,
        None
      )) { result =>
        result shouldBe Left(AlbumServiceCreateError.AlbumsToMergeHaveNoPictures)
      }
    }
  }

  "AlbumService#create[private]" should {
    "create Album with inLibrary and AlbumType" in {
      whenReady(service.create("name", Classification, Source, inLibrary = true, user.id)) { albumResponse =>
        WithId(albumResponse.entity.copy(created = dateTime, updated = dateTime), albumResponse.id) shouldBe album
      }
    }
  }

  "AlbumService#update" should {
    "update album if everything is OK" in {
      whenReady(service.update(emptyAlbumId, Some("newName"), Some("description"), Some(Classification))(user)) { result =>
        result shouldBe a[Right[_, _]]

        result.foreach { albumResponse =>
          WithId(albumResponse.entity.copy(created = dateTime, updated = dateTime), albumResponse.id) shouldBe album
        }
      }
    }

    "reject on forbidden album" in {
      whenReady(service.update(forbiddenAlbumId, Some("newName"), None, Some(Classification))(user)) { result =>
        result shouldBe Left(AlbumServiceError.AccessDenied)
      }
    }

    "reject label mode change on album with tagged pictures" in {
      whenReady(service.update(albumId, Some("newName"), None, Some(Localization))(user)) { result =>
        result should matchPattern { case Left(AlbumServiceError.AlbumLabelModeLocked(_)) => }
      }
    }

    "reject label mode change on album in progress" in {
      whenReady(service.update(albumInProgressId, Some("newName"), None, Some(Localization))(user)) { result =>
        result should matchPattern { case Left(AlbumServiceError.AlbumLabelModeLocked(_)) => }
      }
    }

    "reject label mode change on derived album" in {
      whenReady(service.update(derivedAlbumId, Some("newName"), None, Some(Localization))(user)) { result =>
        result should matchPattern { case Left(AlbumServiceError.AlbumLabelModeLocked(_)) => }
      }
    }

    "reject on name taken" in {
      whenReady(service.update(albumId, Some("nameTaken"), None, Some(Classification))(user)) { result =>
        result shouldBe Left(AlbumNameValidationError.AlbumNameTaken)
      }
    }
  }

  "AlbumService#preDelete" should {

    "delete all pictures of Album" in {
      whenReady(service.preDelete(album)) { result =>
        result shouldBe Right(())
      }
    }

  }

  "AlbumService#ensureCanDelete" should {

    "reject if album is forbidden" in {
      whenReady(service.ensureCanDelete(forbiddenAlbum, user)) { result =>
        result shouldBe Left(AlbumServiceError.AccessDenied)
      }
    }

    "reject if album is in process" in {
      whenReady(service.ensureCanDelete(albumInProgress, user)) { result =>
        result shouldBe a[Left[_, _]]
      }
    }

  }

  "AlbumService#clone" should {

    "clone only selected pictures to new album" in {
      whenReady(service.clone(albumId, Some(Seq("1", "2")), Some("name"), None, true, None, None)(user)) { result =>
        assert(result.right.exists { albumResponse =>
          WithId(albumResponse.entity.copy(
            created = dateTime,
            updated = dateTime
          ), albumResponse.id) === album
        })
      }
    }

    "clone whole album when no pictures are selected" in {
      whenReady(service.clone(albumId, None, Some("name"), Some("description"), true, None, None)(user)) { result =>
        assert(result.right.exists { albumResponse =>
          WithId(albumResponse.entity.copy(
            created = dateTime,
            updated = dateTime
          ), albumResponse.id) === album
        })
      }
    }

    "reject on forbidden album" in {
      whenReady(service.clone(forbiddenAlbumId, Some(Seq("1", "2")), Some("newName"), None, true, None, None)(user)) { result =>
        result shouldBe Left(AlbumServiceError.AccessDenied)
      }
    }

    "reject on name taken" in {
      whenReady(service.clone(emptyAlbumId, Some(Seq("1", "2")), Some("nameTaken"), None, true, None, None)(user)) { result =>
        result shouldBe Left(AlbumNameValidationError.AlbumNameTaken)
      }
    }

    "reject on empty name" in {
      whenReady(service.clone(emptyAlbumId, Some(Seq("1", "2")), Some(""), None, true, None, None)(user)) { result =>
        result shouldBe Left(AlbumNameValidationError.AlbumNameIsEmpty)
      }
    }

    "reject when album is not active" in {
      whenReady(service.clone(albumInProgressId, Some(Seq("1", "2")), Some("newName"), None, true, None)(user)) { result =>
        result shouldBe Left(AlbumServiceError.AlbumIsNotActive)
      }
    }
  }

  "AlbumService#augmentpictures" should {

    "return augmented album" in {
      when(dao.create(any[String => Album])(any[ExecutionContext]))
        .thenReturn(future(WithId(album.entity.copy(status = AlbumStatus.Saving, inLibrary = false), album.id)))
      whenReady(service.augmentPictures(
        augmentationParams, Some("name"), albumId, true, 1, None
      )) { result =>
        assert(
          result.right.get.entity.copy(
            created = dateTime,
            updated = dateTime
          ) == album.entity.copy(status = AlbumStatus.Saving, inLibrary = false)
        )
      }
    }

    "reject when input album doesn't exist" in {
      whenReady(service.augmentPictures(
        augmentationParams, Some("demo"), noAlbumId, true, 1, None
      )(user)) {
        result => result shouldBe Left(AlbumServiceError.AlbumNotFound)
      }
    }

    "reject when output album name is empty" in {
      whenReady(service.augmentPictures(
        augmentationParams, Some(""), emptyAlbumId, true, 1, None
      )(user)) {
        result => result shouldBe Left(AlbumNameValidationError.AlbumNameIsEmpty)
      }
    }

    "reject when output album name is taken" in {
      whenReady(service.augmentPictures(
        augmentationParams, Some("nameTaken"), emptyAlbumId, true, 1, None
      )(user)) {
        result => result shouldBe Left(AlbumNameValidationError.AlbumNameTaken)
      }
    }

    "reject when invalid rotation augmentation request params are provided" in {
      whenReady(service.augmentPictures(
        invalidRotateAugmentationParams, Some("name"), albumId, true, 1, None
      )(user)) {
        result => assert(result.isLeft)
      }
    }

    "reject when invalid shearing augmentation request params are provided" in {
      whenReady(service.augmentPictures(
        invalidShearingAugmentationParams, Some("name"), albumId, true, 1, None
      )(user)) {
        result => assert(result.isLeft)
      }
    }

    "reject when invalid noising augmentation request params are provided" in {
      whenReady(service.augmentPictures(
        invalidNoisingAugmentationParams, Some("name"), albumId, true, 1, None
      )(user)) {
        result => assert(result.isLeft)
      }
    }

  }

  "AlbumService#save" should {

    "should save the album successfully" in {

      when(dao.count(filterContains(NameIs("name")))(any[ExecutionContext])).thenReturn(future(0))
      whenReady(service.save(albumId, "name")) { result =>
        result shouldBe Right(album)
      }
    }

    "return error response when album name already exists" in {
      when(dao.count(filterContains(NameIs("name")))(any[ExecutionContext])).thenReturn(future(1))
      whenReady(service.save(albumId, "name")) { result =>
        result shouldBe Left(AlbumNameValidationError.AlbumNameTaken)
      }
    }

    "return error response when album name is empty" in {
      whenReady(service.save(albumId, "")) { result =>
        result shouldBe Left(AlbumNameValidationError.AlbumNameIsEmpty)
      }
    }
  }

  "AlbumService#predelete" should {

    "should  delete the album successfully" in {
      when(projectService.removeAssetFromAllProjects(any[AssetReference])(any[User])).thenReturn(future(()))
      when(processService.cancelProcesses(any[String], eqTo(AssetType.TabularModel))(eqTo(user)))
        .thenReturn(future(Right(())))
      when(service.get(any[String])(any[User])).thenReturn(future(Right(album)))
      when(service.delete(any[String])(any[User])).thenReturn(future(Right(())))
      when(assetSharingService.deleteSharesForAsset(any[String], eqTo(AssetType.TabularModel))(eqTo(user)))
        .thenReturn(future(()))
      when(imagesCommonService.deletePictures(any[String])).thenReturn(future(()))
        whenReady(service.preDelete(album)) { response =>
      response shouldBe Right(())
      }
    }
  }

  "AlbumService#generateAlbumStorageAccessParams" should {

    "generate access credentials" in {
      when(dao.get(eqTo(album.id))(any[ExecutionContext])).thenReturn(future(Some(album)))
      whenReady(service.generateAlbumStorageAccessParams(album.id)) { result =>
        result shouldBe Right(AlbumStorageAccessParameters(tempCredentials, "album-prefix"))
      }
    }

    "return error if album with given id not found" in {
      when(dao.get(eqTo(noAlbumId))(any[ExecutionContext])).thenReturn(future(None))
      whenReady(service.generateAlbumStorageAccessParams(noAlbumId)) { result =>
        result shouldBe Left(AlbumNotFound)
      }
    }

  }

}
