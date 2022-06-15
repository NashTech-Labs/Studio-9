package baile.services.images

import java.util.UUID

import akka.event.LoggingAdapter
import baile.dao.asset.Filters.{ NameIs, OwnerIdIs }
import baile.dao.images.PictureDao.{ AlbumIdIn, AlbumIdIs, HasTags }
import baile.dao.images.{ AlbumDao, PictureDao }
import baile.daocommons.WithId
import baile.daocommons.filters.{ Filter, IdIn, IdIs }
import baile.domain.images._
import baile.domain.images.augmentation._
import baile.services.images.ImagesCommonService.AugmentationResultImage
import baile.services.remotestorage.RemoteStorageService
import baile.utils.TryExtensions._
import baile.utils.{ CollectionProcessing, UniqueNameGenerator }
import cats.Id
import cats.implicits._
import cortex.api.job.album.{ augmentation => CortexAugmentation }
import cortex.api.job.album.common.{ Image, Tag, TagArea, TaggedImage }
import cortex.api.job.computervision.PredictedTag

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

class ImagesCommonService(
  pictureDao: PictureDao,
  albumDao: AlbumDao,
  pictureStorage: RemoteStorageService,
  imagesProcessingBatchSize: Int,
  val storagePathPrefix: String
)(
  implicit val ec: ExecutionContext,
  val logger: LoggingAdapter
) {

  private[services] def getAlbum(id: String): Future[Option[WithId[Album]]] =
    albumDao.get(id)

  private[services] def getAlbumMandatory(id: String): Future[WithId[Album]] =
    getAlbum(id).map(_.getOrElse(throw new RuntimeException(s"Unexpectedly not found album $id in storage")))

  private[services] def countUserAlbums(name: String, id: Option[String], userId: UUID): Future[Int] = {
    val baseFilter = NameIs(name) && OwnerIdIs(userId)
    val filter = id.fold(baseFilter)(!IdIs(_) && baseFilter)
    albumDao.count(filter)
  }

  private[services] def getImagesPathPrefix(album: Album): String =
    pictureStorage.path(storagePathPrefix, album.picturesPrefix)

  private[services] def getPictures(albumId: String, onlyTagged: Boolean): Future[Seq[WithId[Picture]]] =
    getPictures(Seq(albumId), onlyTagged)

  private[services] def getPictures(albumIds: Seq[String], onlyTagged: Boolean): Future[Seq[WithId[Picture]]] =
    pictureDao.listAll(buildPicturesFilter(albumIds, onlyTagged))

  private[services] def getPictures(
    albumIds: Seq[String],
    onlyTagged: Boolean,
    pictureIds: Seq[String]
  ): Future[Seq[WithId[Picture]]] =
    pictureDao.listAll(buildPicturesFilter(albumIds, onlyTagged) && IdIn(pictureIds))

  private[services] def getPictures(
    albumId: String,
    pictureIds: Seq[String]
  ): Future[Seq[WithId[Picture]]] = pictureDao.listAll(AlbumIdIs(albumId) && IdIn(pictureIds))

  private[services] def countPictures(albumId: String, onlyTagged: Boolean): Future[Int] =
    countPictures(Seq(albumId), onlyTagged)

  private[services] def countPictures(albumIds: Seq[String], onlyTagged: Boolean): Future[Int] =
    pictureDao.count(buildPicturesFilter(albumIds, onlyTagged))

  private[services] def attachPictures(albumId: String, pictures: Seq[Picture]): Future[Unit] =
    pictureDao.createMany(pictures.map(_.copy(albumId = albumId))).map(_ => ())

  private[services] def deletePictures(albumId: String): Future[Unit] =
    pictureDao.deleteMany(AlbumIdIs(albumId)).map(_ => ())

  private[services] def updateAlbumStatus(albumId: String, status: AlbumStatus): Future[Option[WithId[Album]]] =
    albumDao.update(albumId, _.copy(status = status))

  private[services] def makeAlbumActive(albumId: String): Future[Option[WithId[Album]]] =
    updateAlbumStatus(albumId, AlbumStatus.Active)

  private[services] def convertCortexTagToPictureTag(
    tag: Tag,
    labelMode: AlbumLabelMode,
    confidence: Option[Double] = None
  ): Try[PictureTag] = Try {
    PictureTag(
      label = tag.label,
      area = labelMode match {
        case AlbumLabelMode.Classification =>
          None
        case AlbumLabelMode.Localization =>
          val area = tag.area.getOrElse(throw new RuntimeException(
            "Not found tag area in cortex output tag for localization album"
          ))
          Some(PictureTagArea(
            top = area.top,
            left = area.left,
            height = area.height,
            width = area.width
          ))
      },
      confidence = confidence
    )
  }



  private[services] def convertCortexTagToPictureTag(tag: PredictedTag, labelMode: AlbumLabelMode): Try[PictureTag] =
    convertCortexTagToPictureTag(tag.getTag, labelMode, Some(tag.confidence))

  private[services] def convertPicturesToCortexTaggedImages(pictures: Seq[WithId[Picture]]): Seq[TaggedImage] =
    pictures.map { picture =>
      val image = Image(
        picture.entity.filePath,
        referenceId = Some(picture.id),
        fileSize = picture.entity.fileSize,
        displayName = Some(picture.entity.fileName)
      )
      val tags = picture.entity.tags.map { tag =>
        Tag(
          label = tag.label,
          area = tag.area.map { area =>
            TagArea(
              top = area.top,
              left = area.left,
              height = area.height,
              width = area.width
            )
          }
        )
      }
      TaggedImage(Some(image), tags)
    }

  private[services] def populateAugmentedAlbum(
    inputAlbumId: String,
    outputAlbumId: String,
    labelMode: AlbumLabelMode,
    resultImages: Seq[AugmentationResultImage]
  ): Future[Unit] = {

    def buildResultPicturesBatch(
      originalPicturesBatch: Seq[WithId[Picture]],
      resultImagesBatch: Iterable[AugmentationResultImage]
    ): Try[Seq[Picture]] = {

      def getOriginalPicture(
        pictureId: String
      ): Try[WithId[Picture]] = Try {
        originalPicturesBatch
          .find(_.id == pictureId)
          .getOrElse(throw new RuntimeException(
            s"Unexpectedly not found original picture $pictureId for result image"
          ))
      }

      def buildResultTags(resultImage: AugmentationResultImage, originalPicture: Picture): Try[Seq[PictureTag]] =
        Try.sequence(resultImage.image.tags.map(convertCortexTagToPictureTag(_, labelMode)))

      resultImagesBatch.toList.foldM(Seq.empty[Picture]) {
        case (builtPictures, resultImage) =>
          for {
            WithId(originalPicture, originalPictureId) <- getOriginalPicture(resultImage.image.getImage.getReferenceId)
            resultTags <- buildResultTags(resultImage, originalPicture)
            appliedAugmentations = resultImage
              .appliedAugmentations
              .map(convertCortexAppliedAugmentationToAppliedAugmentation)
            newName = UniqueNameGenerator.generateUniqueName[Id](
              prefix = (originalPicture.fileName +: appliedAugmentations.map(_.generalParams.augmentationType))
                .mkString("_"),
              suffixDelimiter = " "
            )(name => !builtPictures.exists(_.fileName == name))
          } yield {
            builtPictures :+ Picture(
              albumId = outputAlbumId,
              filePath = resultImage.image.getImage.filePath,
              fileName = newName,
              fileSize = resultImage.fileSize.orElse(originalPicture.fileSize),
              caption = originalPicture.caption,
              tags = resultTags,
              originalPictureId = Some(originalPictureId),
              appliedAugmentations = Some(
                originalPicture.appliedAugmentations.getOrElse(Seq.empty) ++ appliedAugmentations
              ),
              predictedCaption = None,
              predictedTags = Seq.empty,
              meta = originalPicture.meta
            )
          }
      }

    }

    def handleImagesChunk(group: Iterable[AugmentationResultImage]): Future[Unit] = {
      for {
        originalPicturesBatch <- getPictures(inputAlbumId, group.map(_.image.getImage.getReferenceId).toSeq)
        resultPicturesBatch <- buildResultPicturesBatch(originalPicturesBatch, group).toFuture
        _ <- attachPictures(outputAlbumId, resultPicturesBatch)
      } yield ()
    }

    CollectionProcessing.handleIterableInParallelBatches(
      elements = resultImages,
      handler = handleImagesChunk,
      batchSize = imagesProcessingBatchSize,
      parallelismLevel = 1
    )
  }

  private def convertCortexAppliedAugmentationToAppliedAugmentation(
    appliedAugmentation: CortexAugmentation.AppliedAugmentation
  ): AppliedAugmentation = {
    import CortexAugmentation.AppliedAugmentation.GeneralParams
    val generalParams = appliedAugmentation.generalParams match {
      case params: GeneralParams.RotationParams =>
        AppliedRotationParams(params.value.angle, params.value.resize)
      case params: GeneralParams.BlurringParams =>
        AppliedBlurringParams(params.value.sigma)
      case params: GeneralParams.ShearingParams =>
        AppliedShearingParams(params.value.angle, params.value.resize)
      case params: GeneralParams.NoisingParams =>
        AppliedNoisingParams(params.value.noiseSignalRatio)
      case params: GeneralParams.ZoomInParams =>
        AppliedZoomInParams(params.value.magnification, params.value.resize)
      case params: GeneralParams.ZoomOutParams =>
        AppliedZoomOutParams(params.value.shrinkFactor, params.value.resize)
      case params: GeneralParams.OcclusionParams =>
        AppliedOcclusionParams(
          params.value.occAreaFraction,
          occlusionModeToDomain(params.value.mode),
          params.value.isSarAlbum,
          params.value.tarWinSize
        )
      case params: GeneralParams.TranslationParams =>
        AppliedTranslationParams(
          params.value.translateFraction,
          translationModeToDomain(params.value.mode),
          params.value.resize
        )
      case params: GeneralParams.SaltPepperParams =>
        AppliedSaltPepperParams(params.value.knockoutFraction, params.value.pepperProbability)
      case params: GeneralParams.MirroringParams =>
        AppliedMirroringParams(mirroringAxisFlipToDomain(params.value.axisFlipped))
      case params: GeneralParams.CroppingParams =>
        AppliedCroppingParams(params.value.cropAreaFraction, params.value.resize)
      case params: GeneralParams.PhotometricDistortParams =>
        AppliedPhotometricDistortParams(
          params.value.alphaConstant,
          params.value.deltaMax,
          params.value.alphaSaturation,
          params.value.deltaHue
        )
      case GeneralParams.Empty => throw new RuntimeException("Invalid applied augmentation")
    }
    AppliedAugmentation(
      generalParams = generalParams,
      extraParams = appliedAugmentation.extraParams,
      internalParams = appliedAugmentation.internalParams
    )
  }

  private def occlusionModeToDomain(occlusionMode: CortexAugmentation.OcclusionMode): OcclusionMode = {
    occlusionMode match {
      case CortexAugmentation.OcclusionMode.BACKGROUND => OcclusionMode.Background
      case CortexAugmentation.OcclusionMode.ZERO => OcclusionMode.Zero
      case CortexAugmentation.OcclusionMode.Unrecognized(_) => throw new RuntimeException("Unrecognized occlusion mode")
    }
  }

  private def translationModeToDomain(
    translationMode: CortexAugmentation.TranslationMode
  ): TranslationMode = {
    translationMode match {
      case CortexAugmentation.TranslationMode.CONSTANT =>
        TranslationMode.Constant
      case CortexAugmentation.TranslationMode.REFLECT =>
        TranslationMode.Reflect
      case CortexAugmentation.TranslationMode.Unrecognized(_) =>
        throw new RuntimeException("Unrecognized translation mode")
    }
  }

  private def mirroringAxisFlipToDomain(
    mirroringAxisToFlip: CortexAugmentation.MirroringAxisToFlip
  ): MirroringAxisToFlip = {
    mirroringAxisToFlip match {
      case CortexAugmentation.MirroringAxisToFlip.HORIZONTAL =>
        MirroringAxisToFlip.Horizontal
      case CortexAugmentation.MirroringAxisToFlip.VERTICAL =>
        MirroringAxisToFlip.Vertical
      case CortexAugmentation.MirroringAxisToFlip.BOTH =>
        MirroringAxisToFlip.Both
      case CortexAugmentation.MirroringAxisToFlip.Unrecognized(_) =>
        throw new RuntimeException("Unrecognized mirroring axis to flip mode")
    }
  }

  private def buildPicturesFilter(albumIds: Seq[String], onlyLabeled: Boolean): Filter = {
    val albumsFilter = albumIds match {
      case Seq(albumId) => AlbumIdIs(albumId)
      case _ => AlbumIdIn(albumIds)
    }
    if (onlyLabeled) albumsFilter && HasTags else albumsFilter
  }


}

object ImagesCommonService {

  case class AugmentationResultImage(
    image: TaggedImage,
    fileSize: Option[Long],
    appliedAugmentations: Seq[CortexAugmentation.AppliedAugmentation]
  )

}
