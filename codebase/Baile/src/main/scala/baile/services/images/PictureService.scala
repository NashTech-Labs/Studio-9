package baile.services.images

import akka.NotUsed
import akka.event.LoggingAdapter
import akka.stream.Materializer
import akka.stream.alpakka.csv.scaladsl.CsvFormatting
import akka.stream.scaladsl.Source
import akka.util.ByteString
import baile.dao.asset.Filters.SearchQuery
import baile.dao.images.PictureDao
import baile.dao.images.PictureDao._
import baile.daocommons.WithId
import baile.daocommons.filters.{ FalseFilter, Filter, IdIs, TrueFilter }
import baile.daocommons.sorting.Field
import baile.domain.images._
import baile.domain.images.augmentation.AugmentationType
import baile.domain.usermanagement.User
import baile.services.common.{ EntityService, FileUploadService }
import baile.services.common.EntityService.WithSortByField
import baile.services.images.AlbumService.AlbumServiceError
import baile.services.images.PictureService.{ PictureServiceError, PictureServiceExportError }
import baile.services.images.exceptions.UnexpectedAlbumResponseException
import baile.services.remotestorage.RemoteStorageService
import cats.data.EitherT
import cats.implicits._

import scala.concurrent.{ ExecutionContext, Future }

class PictureService(
  commonService: ImagesCommonService,
  albumService: AlbumService,
  pictureStorage: RemoteStorageService,
  fileUploadService: FileUploadService,
  val dao: PictureDao
)(
  implicit val ec: ExecutionContext,
  val logger: LoggingAdapter
) extends EntityService[Picture, PictureServiceError] with WithSortByField[Picture, PictureServiceError] {

  override val notFoundError: PictureServiceError = PictureServiceError.PictureNotFound
  override val sortingFieldNotFoundError: PictureServiceError = PictureServiceError.SortingFieldUnknown

  override protected val findField: String => Option[Field] = Map(
    "fileName" -> PictureDao.FileName,
    "fileSize" -> PictureDao.FileSize,
    "caption" -> PictureDao.Caption,
    "predictedCaption" -> PictureDao.PredictedCaption,
    "tags.label" -> PictureDao.Labels,
    "predictedTags.label" -> PictureDao.PredictedLabels
  ).get

  def create(
    albumId: String,
    pictureName: String,
    uploadedFilePath: String,
    fileName: String
  )(implicit user: User, materializer: Materializer): Future[Either[PictureServiceError, WithId[Picture]]] = {

    def preparePicturePath(fileName: String): String =
      java.util.UUID.randomUUID().toString + "-" + fileName


    def detectPictureMimeType(bytes: ByteString): Either[PictureServiceError, String] = {
      // see https://en.wikipedia.org/wiki/List_of_file_signatures
      if (bytes.startsWith("GIF89a".getBytes()) || bytes.startsWith("GIF87a".getBytes())) {
        Right("image/gif")
      } else if (bytes.startsWith(ByteString(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))) {
        Right("image/png")
      } else if (bytes.startsWith(ByteString(0xFF, 0xD8, 0xFF))) {
        Right("image/jpeg")
      } else {
        Left(PictureServiceError.PictureTypeUnknown)
      }
    }

    def validatePictureFormat(): Future[Either[PictureServiceError, Unit]] =
      for {
        streamedFile <- fileUploadService.fileStorage.streamFile(uploadedFilePath)
        headerBytes <- streamedFile.content.mapConcat(identity).take(10).runFold(ByteString.empty)(_ :+ _)
      } yield detectPictureMimeType(headerBytes).map(_ => ())

    def savePicture(album: WithId[Album]): Future[WithId[Picture]] = {
      val picturePath = preparePicturePath(fileName)
      val filePath = pictureStorage.path(
        commonService.getImagesPathPrefix(album.entity),
        picturePath
      )

      for {
        fileInfo <- pictureStorage.copyFrom(fileUploadService.fileStorage, uploadedFilePath, filePath)
        pictureWithId <- dao.create(_ => Picture(
          albumId = album.id,
          filePath = picturePath,
          fileName = pictureName,
          fileSize = Some(fileInfo.size),
          caption = None,
          predictedCaption = None,
          tags = Seq.empty,
          predictedTags = Seq.empty,
          meta = Map.empty,
          originalPictureId = None,
          appliedAugmentations = None
        ))
        _ <- fileUploadService.deleteUploadedFile(uploadedFilePath)
      } yield pictureWithId
    }

    val result = for {
      album <- EitherT(getAlbum(albumId, user))
      _ <- EitherT(ensurePictureOperationAvailable(album, user))
      _ <- EitherT(validatePictureFormat())
      pictureWithId <- EitherT.right[PictureServiceError](savePicture(album))
    } yield pictureWithId

    result.value
  }

  def update(
    albumId: String,
    pictureId: String,
    newCaption: Option[String],
    newTags: Seq[PictureTag]
  )(implicit user: User): Future[Either[PictureServiceError, WithId[Picture]]] = {

    val result = for {
      albumWithId <- EitherT(getAlbum(albumId, user))
      _ <- EitherT(ensurePictureOperationAvailable(albumWithId, user))
      _ <- EitherT(get(albumId, pictureId))
      picture <- EitherT(update(pictureId, _.copy(caption = newCaption, tags = newTags)))
    } yield picture

    result.value
  }

  def get(
    albumId: String,
    pictureId: String,
    sharedResourceId: Option[String] = None
  )(implicit user: User): Future[Either[PictureServiceError, WithId[Picture]]] = {
    val result = for {
      _ <- EitherT(getAlbum(albumId, user, sharedResourceId))
      withIdOption <- EitherT.right[PictureServiceError](dao.get(AlbumIdIs(albumId) && IdIs(pictureId)))
      result <- EitherT.fromEither[Future](ensureEntityFound(withIdOption))
    } yield result
    result.value
  }

  def delete(
    albumId: String,
    pictureId: String
  )(implicit user: User): Future[Either[PictureServiceError, Unit]] = {
    val result = for {
      albumWithId <- EitherT(getAlbum(albumId, user))
      _ <- EitherT(ensurePictureOperationAvailable(albumWithId, user))
      _ <- EitherT(get(albumId, pictureId))
      result <- EitherT(delete(pictureId))
    } yield result
    result.value
  }

  def list(
    albumId: String,
    labels: Option[Seq[String]],
    search: Option[String],
    orderBy: Seq[String],
    page: Int,
    pageSize: Int,
    sharedResourceId: Option[String] = None,
    augmentationTypes: Option[Seq[Option[AugmentationType]]] = None
  )(implicit user: User): Future[Either[PictureServiceError, (Seq[WithId[Picture]], Int)]] = {
    val result = for {
      album <- EitherT(getAlbum(albumId, user, sharedResourceId))
      labelFilter <- EitherT.rightT[Future, PictureServiceError](prepareLabelFilter(album.entity, labels))
      searchFilter <- EitherT.rightT[Future, PictureServiceError](prepareListFilter(search))
      augmentationFilter <- EitherT.rightT[Future, PictureServiceError](prepareAugmentationTypeFilter(
        augmentationTypes
      ))
      result <- EitherT(list(
        AlbumIdIs(albumId) && labelFilter && searchFilter && augmentationFilter,
        orderBy,
        page,
        pageSize
      ))
    } yield result
    result.value
  }

  def getLabelsStats(
    albumId: String,
    sharedResourceId: Option[String] = None
  )(implicit user: User): Future[Either[PictureServiceError, Map[String, Int]]] = {
    val result = for {
      album <- EitherT(getAlbum(albumId, user, sharedResourceId))
      stats <- EitherT.right[PictureServiceError](getLabelStatsByAlbumType(album))
    } yield stats

    result.value
  }

  def signPicture(
    albumId: String,
    picture: WithId[Picture],
    sharedResourceId: Option[String] = None
  )(implicit user: User): Future[Either[PictureServiceError, WithId[Picture]]] = {
    signPictures(albumId, Seq(picture), sharedResourceId).map(_.map(_.head))
  }

  def signPictures(
    albumId: String,
    pictures: Seq[WithId[Picture]],
    sharedResourceId: Option[String] = None
  )(implicit user: User): Future[Either[PictureServiceError, Seq[WithId[Picture]]]] = {
    getAlbum(albumId, user, sharedResourceId).map(_.map { album =>
      pictures.map { picture =>
        if (picture.entity.albumId != album.id) throw new RuntimeException("picture doesn't belong to album")
        val fullPicturePath: String = pictureStorage.path(
          commonService.getImagesPathPrefix(album.entity),
          picture.entity.filePath
        )
        val externalUrl = pictureStorage.getExternalUrl(fullPicturePath)
        WithId(picture.entity.copy(filePath = externalUrl), picture.id)
      }
    })
  }

  def addPictures(
    albumId: String,
    pictures: Seq[Picture],
    keepExisting: Boolean
  )(implicit user: User): Future[Either[PictureServiceError, Unit]] = {

    def removePicturesIfNotKeepingExisting: Future[Unit] = {
      if (!keepExisting) dao.deleteMany(AlbumIdIs(albumId)).map(_ => ())
      else Future.successful(())
    }

    def validatePicturesNotEmpty: Either[PictureServiceError, Unit] = Either.cond(
      pictures.nonEmpty,
      (),
      PictureServiceError.PicturesNotFound
    )

    val result = for {
      _ <- EitherT.fromEither[Future](validatePicturesNotEmpty)
      albumWithId <- EitherT(getAlbum(albumId, user))
      _ <- EitherT(ensurePictureOperationAvailable(albumWithId, user))
      _ <- EitherT.right[PictureServiceError](removePicturesIfNotKeepingExisting)
      _ <- EitherT.right[PictureServiceError](commonService.attachPictures(albumId, pictures))
    } yield ()

    result.value
  }

  def exportLabels(
    albumId: String,
    sharedResourceId: Option[String] = None
  )(implicit user: User): Future[Either[PictureServiceExportError, Source[ByteString, NotUsed]]] = {

    def loadAlbum: Future[Either[PictureServiceExportError, WithId[Album]]] =
      albumService.get(albumId).map {
        _.leftMap {
          case AlbumServiceError.AlbumNotFound => PictureServiceExportError.AlbumNotFound
          case AlbumServiceError.AccessDenied => PictureServiceExportError.AccessDenied
          case error => throw UnexpectedAlbumResponseException(error)
        }
      }

    def writeData(album: Album, data: Source[PictureTagsSummary, NotUsed]): Source[ByteString, NotUsed] = {

      def extractTags(row: PictureTagsSummary) = {
        album.`type` match {
          case AlbumType.Source => row.tags
          case _ => row.predictedTags
        }
      }

      def writeClassificationData(data: Source[PictureTagsSummary, NotUsed]): Source[ByteString, NotUsed] = {
        data
          .map { row =>
            val tags = extractTags(row)
            List(row.fileName, tags.headOption.map(_.label).getOrElse(""))
          }
          .prepend(Source.single(List("filename", "label")))
          .via(CsvFormatting.format())
      }

      def writeLocalizationData(data: Source[PictureTagsSummary, NotUsed]): Source[ByteString, NotUsed] = {
        data
          .mapConcat { row =>
            val tags = extractTags(row)
            // we skip broken localization tags (with no area)
            val tagRows = tags.collect {
              case PictureTag(label, Some(area), _) => List(
                label,
                area.left.toString,
                area.top.toString,
                (area.left + area.width).toString,
                (area.top + area.height).toString
              )
            }
            if (tagRows.nonEmpty) tagRows.map(row.fileName :: _).toList
            else List(row.fileName :: List.fill(5)(""))
          }
          .prepend(Source.single(List("filename", "label", "x-min", "y-min", "x-max", "y-max")))
          .via(CsvFormatting.format())
      }

      album.labelMode match {
        case AlbumLabelMode.Classification => writeClassificationData(data)
        case AlbumLabelMode.Localization => writeLocalizationData(data)
      }
    }

    val result = for {
      album <- EitherT(loadAlbum)
      tagsData = dao.exportTags(albumId)
      source = writeData(album.entity, tagsData)
    } yield source

    result.value
  }

  private def getLabelStatsByAlbumType(album: WithId[Album]): Future[Map[String, Int]] = {
    album.entity.`type` match {
      case AlbumType.Source => dao.getLabelsStats(album.id)
      case AlbumType.Derived => dao.getAllLabelsStats(album.id)
      case AlbumType.TrainResults => dao.getPredictedLabelsStats(album.id)
    }
  }

  private def getAlbum(
    albumId: String,
    user: User,
    sharedResourceId: Option[String] = None
  ): Future[Either[PictureServiceError, WithId[Album]]] =
    albumService.get(albumId, sharedResourceId)(user).map {
      _.leftMap {
        case AlbumServiceError.AlbumNotFound => PictureServiceError.AlbumNotFound
        case AlbumServiceError.AccessDenied => PictureServiceError.AccessDenied
        case error => throw UnexpectedAlbumResponseException(error)
      }
    }

  private[images] def ensurePictureOperationAvailable(
    album: WithId[Album],
    user: User
  ): Future[Either[PictureServiceError, Unit]] = {
    val result = for {
      _ <- EitherT.fromEither[Future](ensureAlbumIsSource(album))
      _ <- EitherT.cond[Future].apply[PictureServiceError, Unit](
        album.entity.status == AlbumStatus.Active,
        (),
        PictureServiceError.PictureOperationUnavailable(
          "Can not perform operation on pictures because album is in progress of something")
      )
      _ <- EitherT.cond[Future].apply[PictureServiceError, Unit](
        album.entity.video.isEmpty,
        (),
        PictureServiceError.PictureOperationUnavailable(
          "Can not perform operation on pictures because album contains video")
      )
      _ <- EitherT(albumService.ensureCanUpdateContent(album, user)).leftMap[PictureServiceError] { _ =>
        PictureServiceError.PictureOperationUnavailable(
          "Album cannot be modified because it is already in use another Asset." +
            "Please Clone this Album and make modifications to the Cloned Album"
        )
      }
    } yield ()

    result.value
  }

  private def ensureAlbumIsSource(album: WithId[Album]): Either[PictureServiceError, Unit] = {
    Either.cond[PictureServiceError, Unit](
      album.entity.`type` == AlbumType.Source,
      (),
      PictureServiceError.PictureOperationUnavailable("invalid album type")
    )
  }

  private def prepareLabelFilter(album: Album, optionalLabels: Option[Seq[String]]): Filter = {

    optionalLabels.map { labels =>
      val labelsFilter = labels.filter(_.nonEmpty) match {
        case nonEmptyLabels if nonEmptyLabels.nonEmpty => album.`type` match {
          case AlbumType.Source => LabelsAre(nonEmptyLabels)
          case AlbumType.Derived => PredictedLabelsAre(nonEmptyLabels)
          case AlbumType.TrainResults =>
            LabelsAre(nonEmptyLabels) || PredictedLabelsAre(nonEmptyLabels)
        }
        case _ => FalseFilter
      }

      val emptyLabelFilter = labels.find(_.isEmpty) match {
        case Some(_) => album.`type` match {
          case AlbumType.Source => !HasTags
          case AlbumType.Derived => !HasPredictedTags
          case AlbumType.TrainResults =>
            !HasTags || !HasPredictedTags
        }
        case _ => FalseFilter
      }

      labelsFilter || emptyLabelFilter

    }.getOrElse(TrueFilter)

  }

  private def prepareListFilter(search: Option[String]): Filter =
    search match {
      case Some(term) if term.length > 0 => SearchQuery(term)
      case _ => TrueFilter
    }

  private def prepareAugmentationTypeFilter(
    optionalAugmentationTypes: Option[Seq[Option[AugmentationType]]]
  ): Filter = {

    optionalAugmentationTypes.map { augmentationTypes =>
      val selectedAugmentationTypes = augmentationTypes.collect {
        case Some(augmentationType) => augmentationType
      }
      val augmentationsFilter =
        if (selectedAugmentationTypes.isEmpty) FalseFilter
        else AugmentationTypesAre(selectedAugmentationTypes)
      val emptyAugmentationFilter = if (augmentationTypes.exists(_.isEmpty)) HasNoAugmentations else FalseFilter

      augmentationsFilter || emptyAugmentationFilter

    }.getOrElse(TrueFilter)

  }

}

object PictureService {

  sealed trait PictureServiceError

  object PictureServiceError {

    case object PictureNotFound extends PictureServiceError

    case object AccessDenied extends PictureServiceError

    case object AlbumNotFound extends PictureServiceError

    case class PictureOperationUnavailable(reason: String) extends PictureServiceError

    case object PictureTypeUnknown extends PictureServiceError

    case object SortingFieldUnknown extends PictureServiceError

    case object PicturesNotFound extends PictureServiceError

  }

  sealed trait PictureServiceExportError

  object PictureServiceExportError {

    case object AccessDenied extends PictureServiceExportError

    case object AlbumNotFound extends PictureServiceExportError

    case object AlbumLabelModeNotSupported extends PictureServiceExportError

  }

}
