package baile.services.images

import java.time.Instant
import java.util.UUID

import akka.event.LoggingAdapter
import baile.dao.images.AlbumDao
import baile.daocommons.WithId
import baile.daocommons.sorting.Field
import baile.domain.asset.AssetType
import baile.domain.common.S3Bucket
import baile.domain.images.AlbumStatus.Active
import baile.domain.images._
import baile.domain.images.augmentation.AugmentationParams
import baile.domain.usermanagement.User
import baile.services.asset.AssetService
import baile.services.asset.AssetService.{
  WithOwnershipTransfer,
  AssetCreateErrors,
  AssetCreateParams,
  WithCreate,
  WithNestedUsageTracking,
  WithProcess,
  WithSharedAccess
}
import baile.services.asset.sharing.AssetSharingService
import baile.services.common.EntityService.WithSortByField
import baile.services.cortex.job.CortexJobService
import baile.services.cortex.job.SupportedCortexJobTypes._
import baile.services.images.AlbumService.AlbumServiceError._
import baile.services.images.AlbumService.AlbumServiceError
import baile.services.images.AlbumService.AlbumServiceCreateError
import baile.services.images.AlbumService.AlbumNameValidationError
import baile.services.process.ProcessService
import baile.services.project.ProjectService
import baile.services.remotestorage.RemoteStorageService
import baile.utils.validation.Option._
import cats.data.EitherT
import cats.implicits._
import com.google.protobuf.ByteString
import cortex.api.job.album.common.Image
import cortex.api.job.album.augmentation.{ AugmentationRequest => CortexAugmentationRequest }
import cortex.api.job.album.uploading.{ InputImage, S3ImagesImportRequest }

import scala.concurrent.{ ExecutionContext, Future }

class AlbumService(
  protected val commonService: ImagesCommonService,
  protected val dao: AlbumDao,
  protected val pictureStorage: RemoteStorageService,
  protected val cortexJobService: CortexJobService,
  protected val processService: ProcessService,
  protected val assetSharingService: AssetSharingService,
  protected val projectService: ProjectService
)(
  implicit val ec: ExecutionContext,
  val logger: LoggingAdapter
) extends AssetService[Album, AlbumServiceError]
  with WithSortByField[Album, AlbumServiceError]
  with WithProcess[Album, AlbumServiceError]
  with WithSharedAccess[Album, AlbumServiceError]
  with WithNestedUsageTracking[Album, AlbumServiceError]
  with WithOwnershipTransfer[Album]
  with WithCreate[Album, AlbumServiceError, AlbumNameValidationError] {

  import baile.services.images.AlbumService.AlbumNameValidationError._

  override val assetType: AssetType = AssetType.Album
  override val notFoundError: AlbumServiceError = AlbumServiceError.AlbumNotFound
  override val forbiddenError: AlbumServiceError = AlbumServiceError.AccessDenied
  override val sortingFieldNotFoundError: AlbumServiceError = AlbumServiceError.SortingFieldUnknown
  override val inUseError: AlbumServiceError = AlbumServiceError.AlbumInUse

  override protected val createErrors: AssetCreateErrors[AlbumNameValidationError] = AlbumNameValidationError
  override protected val findField: String => Option[Field] = Map(
    "name" -> AlbumDao.Name,
    "created" -> AlbumDao.Created,
    "updated" -> AlbumDao.Updated
  ).get

  override def updateOwnerId(album: Album, ownerId: UUID): Album = album.copy(ownerId = ownerId)

  def update(
    id: String,
    newName: Option[String],
    newDescription: Option[String],
    newLabelMode: Option[AlbumLabelMode]
  )(implicit user: User): Future[Either[AlbumServiceError, WithId[Album]]] = {

    def validateLabelModeUpdate(
      album: WithId[Album],
      labelMode: AlbumLabelMode
    ): Future[Either[AlbumServiceError, Unit]] =
      if (album.entity.labelMode != labelMode) {
        if (album.entity.`type` != AlbumType.Source) {
          Future.successful(AlbumServiceError.AlbumLabelModeLocked("album is not of Source type").asLeft)
        } else {
          album.entity.status match {
            case AlbumStatus.Active =>
              commonService.countPictures(id, onlyTagged = true).map { count =>
                Either.cond(count == 0, (), AlbumServiceError.AlbumLabelModeLocked("album contains labels"))
              }
            case AlbumStatus.Uploading =>
              Future.successful(AlbumServiceError.AlbumLabelModeLocked("album is uploading").asLeft)
            case AlbumStatus.Saving =>
              Future.successful(AlbumServiceError.AlbumLabelModeLocked("album is saving").asLeft)
            case AlbumStatus.Failed =>
              Future.successful(AlbumServiceError.AlbumLabelModeLocked("album is failed").asLeft)
          }
        }

      } else {
        Future.successful(().asRight)
      }

    this.update(
      id,
      album => {
        val result = for {
          _ <- EitherT(newName.validate(name => validateAssetName[AlbumServiceError](
            name,
            Option(id),
            AlbumNameIsEmpty,
            AlbumNameTaken
          )))
          _ <- EitherT(newLabelMode.validate(labelMode => validateLabelModeUpdate(album, labelMode)))
        } yield ()
        result.value
      },
      album => album.copy(
        name = newName.getOrElse(album.name),
        labelMode = newLabelMode.getOrElse(album.labelMode),
        updated = Instant.now(),
        description = newDescription orElse album.description
      )
    )

  }

  def create(
    name: Option[String],
    labelMode: AlbumLabelMode,
    mergeParams: Option[MergeAlbumsParams],
    description: Option[String],
    inLibrary: Option[Boolean]
  )(implicit user: User): Future[Either[AlbumServiceCreateError, WithId[Album]]] = {

    def loadAndValidateMergingAlbums(): Future[Either[AlbumServiceCreateError, Seq[WithId[Album]]]] =
      mergeParams match {
        case Some(MergeAlbumsParams(albumIds, onlyLabelled)) =>
          type EitherTFuture[R] = EitherT[Future, AlbumServiceError, R]
          val result = for {
            _ <- EitherT.cond[Future](
              albumIds.nonEmpty,
              (),
              AlbumServiceCreateError.AlbumsToMergeEmpty: AlbumServiceCreateError
            )
            albums <- albumIds
              .map(id => EitherT(get(id)))
              .toList.sequence[EitherTFuture, WithId[Album]]
              .leftMap(_ => AlbumServiceCreateError.AlbumsToMergeNotFound)
            _ <- EitherT.cond[Future](
              albums.forall(_.entity.labelMode == labelMode),
              (),
              AlbumServiceCreateError.AlbumsToMergeIncorrectLabelMode: AlbumServiceCreateError
            )
            picturesCount <- EitherT.right(commonService.countPictures(albums.map(_.id), onlyLabelled))
            _ <- EitherT.cond[Future](
              picturesCount > 0,
              (),
              AlbumServiceCreateError.AlbumsToMergeHaveNoPictures: AlbumServiceCreateError
            )
          } yield albums

          result.value
        case None => Future.successful(Seq.empty.asRight)
      }

    def startMergingAlbums(toAlbum: WithId[Album], fromAlbums: Seq[WithId[Album]]): Future[Unit] = mergeParams match {
      case Some(MergeAlbumsParams(albumIds, onlyLabelled)) if albumIds.nonEmpty =>
        mergeAlbums(toAlbum, fromAlbums, onlyLabelled)
      case _ => Future.successful(())
    }

    def createAlbum(createParams: AssetCreateParams): Future[WithId[Album]] = {
      val now = Instant.now()

      dao.create(id => Album(
        ownerId = user.id,
        name = createParams.name,
        status = Active,
        `type` = AlbumType.Source,
        labelMode = labelMode,
        inLibrary = createParams.inLibrary,
        created = now,
        updated = now,
        picturesPrefix = albumPrefix(id),
        description = description,
        augmentationTimeSpentSummary = None
      ))
    }

    val result = for {
      createParams <- validateAndGetAssetCreateParams(name, inLibrary)
      albumsToMerge <- EitherT(loadAndValidateMergingAlbums())
      album <- EitherT.right[AlbumServiceCreateError](createAlbum(createParams))
      _ <- EitherT.right[AlbumServiceCreateError](startMergingAlbums(album, albumsToMerge))
    } yield album

    result.value
  }

  def signAlbum(album: Album): Album = album.copy(
    video = album.video.map(video => video.copy(
      filePath = pictureStorage.getExternalUrl(pictureStorage.path(
        commonService.getImagesPathPrefix(album),
        video.filePath
      ))
    ))
  )

  def signAlbum(album: WithId[Album]): WithId[Album] = album.copy(
    entity = signAlbum(album.entity)
  )

  def clone(
    albumId: String,
    selectedPictureIds: Option[Seq[String]],
    newName: Option[String],
    newDescription: Option[String],
    copyOnlyTaggedPictures: Boolean,
    inLibrary: Option[Boolean],
    sharedResourceId: Option[String] = None
  )(implicit user: User): Future[Either[AlbumServiceError, WithId[Album]]] = {

    def getPictures: Future[Either[AlbumServiceError, Seq[WithId[Picture]]]] = {
      selectedPictureIds match {
        case Some(pictureIds) if pictureIds.nonEmpty => commonService.getPictures(albumId, pictureIds).map(_.asRight)
        case _ => commonService.getPictures(albumId, copyOnlyTaggedPictures).map(_.asRight)
      }
    }

    def clonePictures(newAlbumId: String, pictures: Seq[Picture], albumType: AlbumType): Future[Unit] = {
      if (albumType == AlbumType.Source) {
        commonService.attachPictures(newAlbumId, pictures)
      } else {
        commonService.attachPictures(newAlbumId, pictures.map { picture =>
          val newPictureTags = picture.predictedTags.map(_.copy(confidence = None))
          picture.copy(
            tags = newPictureTags,
            predictedTags = Seq.empty[PictureTag]
          )
        })
      }
    }

    val result = for {
      album <- EitherT(get(albumId, sharedResourceId))
      _ <- EitherT.cond[Future](album.entity.status == AlbumStatus.Active, (), AlbumIsNotActive)
      createParams <- validateAndGetAssetCreateParams(newName, inLibrary)
      dateTime = Instant.now()
      newAlbum = album.entity.copy(
        ownerId = user.id,
        name = createParams.name,
        description = newDescription orElse album.entity.description,
        `type` = AlbumType.Source,
        status = AlbumStatus.Active,
        updated = dateTime,
        created = dateTime,
        inLibrary = createParams.inLibrary
      )
      pictures <- EitherT(getPictures)
      newAlbumId <- EitherT.right[AlbumServiceError](dao.create(newAlbum))
      _ <- EitherT.right[AlbumServiceError](clonePictures(newAlbumId, pictures.map(_.entity), album.entity.`type`))
    } yield WithId(newAlbum, newAlbumId)

    result.value
  }

  def augmentPictures(
    augmentationParams: Seq[AugmentationParams],
    outputAlbumName: Option[String],
    inputAlbumId: String,
    includeOriginalPictures: Boolean,
    bloatFactor: Int,
    inLibrary: Option[Boolean]
  )(implicit user: User): Future[Either[AlbumServiceError, WithId[Album]]] = {

    def buildAugmentationRequest(
      pictures: Seq[WithId[Picture]],
      inputAlbum: Album,
      outputAlbum: Album
    ): CortexAugmentationRequest = {
      CortexAugmentationRequest(
        images = commonService.convertPicturesToCortexTaggedImages(pictures),
        filePathPrefix = commonService.getImagesPathPrefix(inputAlbum),
        augmentations = AlbumAugmentationUtils.convertToCortexRequestedAugmentations(augmentationParams),
        bloatFactor = Some(bloatFactor),
        targetPrefix = commonService.getImagesPathPrefix(outputAlbum),
        includeOriginalImages = includeOriginalPictures
      )
    }

    def validateAugmentationRequest: Either[AlbumServiceError, Unit] = {
      type EitherE[R] = Either[AlbumServiceError, R]
      val result = augmentationParams.toList.traverse[EitherE, Unit] { augmentationParam =>
        AlbumAugmentationUtils.validateAugmentationRequestParams(
          augmentationParam,
          InvalidAugmentationRequestParamError
        )
      }
      result.map(_ => ())
    }

    def validateBloatFactor: Either[AlbumServiceError, Unit] = Either.cond(
      bloatFactor >= 1,
      (),
      AlbumServiceError.InvalidAugmentationRequestParamError("Invalid bloat factor")
    )

    def createOutputAlbum(createParams: AssetCreateParams, inputAlbum: Album): Future[WithId[Album]] = {
      val now = Instant.now()

      def getAlbumEntity(id: String) = Album(
        ownerId = user.id,
        name = createParams.name,
        status = AlbumStatus.Saving,
        `type` = inputAlbum.`type`,
        labelMode = inputAlbum.labelMode,
        created = now,
        updated = now,
        inLibrary = createParams.inLibrary,
        picturesPrefix = s"albums/$id",
        description = inputAlbum.description,
        augmentationTimeSpentSummary = None
      )
      dao.create(id => getAlbumEntity(id))
    }

    val result = for {
      _ <- EitherT.fromEither[Future](validateBloatFactor)
      _ <- EitherT.fromEither[Future](validateAugmentationRequest)
      inputAlbum <- EitherT(get(inputAlbumId))
      createParams <- validateAndGetAssetCreateParams(outputAlbumName, inLibrary)
      outputAlbum <- EitherT.right[AlbumServiceError](createOutputAlbum(createParams, inputAlbum.entity))
      pictures <- EitherT.right[AlbumServiceError](commonService.getPictures(inputAlbumId, onlyTagged = false))
      augmentationRequest = buildAugmentationRequest(pictures, inputAlbum.entity, outputAlbum.entity)
      jobId <- EitherT.right[AlbumServiceError](cortexJobService.submitJob(augmentationRequest, user.id))
      _ <- EitherT.right[AlbumServiceError](processService.startProcess(
        jobId = jobId,
        targetId = outputAlbum.id,
        targetType = AssetType.Album,
        handlerClass = classOf[ImagesAugmentationResultHandler],
        meta = ImagesAugmentationResultHandler.Meta(outputAlbum.id, inputAlbumId, includeOriginalPictures),
        userId = user.id
      ))

    } yield outputAlbum
    result.value
  }

  def save(id: String, name: String)(implicit user: User): Future[Either[AlbumServiceError, WithId[Album]]] = {
    val result = for {
      _ <- EitherT(validateAssetName(name, Option(id), AlbumNameIsEmpty, AlbumNameTaken))
      album <- EitherT(this.update(id, _.copy(name = name, inLibrary = true)))
    } yield album
    result.value
  }

  override protected[images] def preDelete(
    album: WithId[Album]
  )(implicit user: User): Future[Either[AlbumServiceError, Unit]] = {
    val result = for {
      _ <- EitherT(super.preDelete(album))
      _ <- EitherT.right[AlbumServiceError](commonService.deletePictures(album.id))
    } yield ()

    result.value
  }

  override protected[services] def ensureCanDelete(
    asset: WithId[Album],
    user: User
  ): Future[Either[AlbumServiceError, Unit]] = {
    val checkStatus = asset.entity.status match {
      case AlbumStatus.Saving => AlbumServiceError.AlbumDeleteUnavailable("Save in progress").asLeft
      case AlbumStatus.Uploading => AlbumServiceError.AlbumDeleteUnavailable("Upload in progress").asLeft
      case _ => ().asRight
    }
    val result = for {
      _ <- EitherT.fromEither[Future](checkStatus)
      _ <- EitherT(super.ensureCanDelete(asset, user))
    } yield ()
    result.value
  }

  private[services] def deleteAlbum(albumId: String): Future[Boolean] =
    dao.delete(albumId)

  private[services] def create(
    name: String,
    labelMode: AlbumLabelMode,
    albumType: AlbumType,
    inLibrary: Boolean,
    ownerId: UUID
  ): Future[WithId[Album]] = {

    val now = Instant.now()

    dao.create(id => Album(
      ownerId = ownerId,
      name = name,
      status = Active,
      `type` = albumType,
      labelMode = labelMode,
      inLibrary = inLibrary,
      created = now,
      updated = now,
      picturesPrefix = albumPrefix(id),
      description = None,
      augmentationTimeSpentSummary = None
    ))
  }

  private[services] def update(
    albumId: String,
    status: AlbumStatus,
    inLibrary: Boolean
  ): Future[Option[WithId[Album]]] =
    dao.update(albumId, _.copy(status = status, inLibrary = inLibrary))

  private def albumPrefix(albumId: String) = s"albums/$albumId"

  private def mergeAlbums(
    toAlbum: WithId[Album],
    fromAlbums: Seq[WithId[Album]],
    onlyLabelled: Boolean
  )(implicit user: User): Future[Unit] = {

    def startMonitoring(jobId: UUID, album: WithId[Album]): Future[Unit] = {
      processService.startProcess(
        jobId,
        album.id,
        AssetType.Album,
        classOf[MergeAlbumsResultHandler],
        MergeAlbumsResultHandler.Meta(
          albumId = album.id,
          inputAlbumsIds = fromAlbums.map(_.id),
          onlyLabelled = onlyLabelled
        ),
        user.id
      ).map(_ => ())
    }

    def prepareJobRequest(
      accessOptions: S3Bucket.AccessOptions,
      pictures: Seq[WithId[Picture]]
    ): S3ImagesImportRequest = {
      val prefixMap: Map[String, String] = fromAlbums.map {
        case WithId(album, id) => id -> album.picturesPrefix
      }.toMap

      S3ImagesImportRequest(
        bucketName = accessOptions.bucketName,
        awsRegion = accessOptions.region,
        awsAccessKey = accessOptions.accessKey.getOrElse(""),
        awsSecretKey = accessOptions.secretKey.getOrElse(""),
        awsSessionToken = accessOptions.sessionToken.getOrElse(""),
        imagesPath = pictureStorage.path(commonService.storagePathPrefix),
        labelsCsvPath = "",
        labelsCsvFile = ByteString.EMPTY,
        targetPrefix = commonService.getImagesPathPrefix(toAlbum.entity),
        images = pictures.map {
          case WithId(picture, id) => InputImage(
            baseImage = Some(Image(
              filePath = pictureStorage.path(prefixMap(picture.albumId), picture.filePath),
              referenceId = Some(id)
            )),
            fileSize = picture.fileSize.getOrElse(1L)
          )
        }
      )
    }

    if (fromAlbums.isEmpty) Future.successful(None)
    else pictureStorage match {
      /*
       * TODO: we should remove such a rude code here:
       * - merging operation should be agnostic to storage implementation
       * - merging on JM side should accept input albums paths and output album path only
       */
      case s3Storage: PicturesS3StorageService =>
        for {
          pictures <- commonService.getPictures(fromAlbums.map(_.id), onlyLabelled) if pictures.nonEmpty
          jobRequest = prepareJobRequest(s3Storage.accessOptions, pictures)
          jobId <- cortexJobService.submitJob(jobRequest, user.id)
          _ <- startMonitoring(jobId, toAlbum)
          _ <- dao.update(toAlbum.id, _.copy(status = AlbumStatus.Uploading))
        } yield ()

      case _ => Future.failed(
        new IllegalStateException("Merging albums can't be done when pictures are stored not in S3")
      )
    }
  }

  def generateAlbumStorageAccessParams(
    albumId: String
  )(implicit user: User): Future[Either[AlbumServiceError, AlbumStorageAccessParameters]] = {
    val result = for {
      album <- EitherT(get(albumId))
      albumPath <- EitherT.rightT[Future, AlbumServiceError](commonService.getImagesPathPrefix(album.entity))
      tempCredentials <- EitherT.right[AlbumServiceError](pictureStorage.getTemporaryCredentials(
        albumPath,
        user
      ))
    } yield AlbumStorageAccessParameters(tempCredentials, albumPath)

    result.value
  }

}

object AlbumService {

  sealed trait AlbumServiceError

  object AlbumServiceError {

    case object AlbumNotFound extends AlbumServiceError

    case object AccessDenied extends AlbumServiceError

    case object SortingFieldUnknown extends AlbumServiceError

    case class AlbumLabelModeLocked(reason: String) extends AlbumServiceError

    case class AlbumDeleteUnavailable(reason: String) extends AlbumServiceError

    case object AlbumIsNotActive extends AlbumServiceError

    case object AlbumInUse extends AlbumServiceError

    case class InvalidAugmentationRequestParamError(message: String) extends AlbumServiceError

  }

  sealed trait AlbumServiceCreateError

  object AlbumServiceCreateError {

    case object AlbumsToMergeNotFound extends AlbumServiceCreateError

    case object AlbumsToMergeEmpty extends AlbumServiceCreateError

    case object AlbumsToMergeIncorrectLabelMode extends AlbumServiceCreateError

    case object AlbumsToMergeHaveNoPictures extends AlbumServiceCreateError

  }

  sealed trait AlbumNameValidationError extends AlbumServiceError with AlbumServiceCreateError

  object AlbumNameValidationError extends AssetCreateErrors[AlbumNameValidationError] {

    case object AlbumNameTaken extends AlbumNameValidationError

    case object NameNotSpecified extends AlbumNameValidationError

    case object AlbumNameIsEmpty extends AlbumNameValidationError

    override val nameNotSpecifiedError: AlbumNameValidationError = NameNotSpecified
    override val emptyNameError: AlbumNameValidationError = AlbumNameIsEmpty

    override def nameAlreadyExistsError(name: String): AlbumNameValidationError = AlbumNameTaken
  }

}
