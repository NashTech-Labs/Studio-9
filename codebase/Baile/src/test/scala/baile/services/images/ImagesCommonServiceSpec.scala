package baile.services.images

import java.util.UUID

import baile.ExtendedBaseSpec
import baile.dao.images.{ AlbumDao, PictureDao }
import baile.daocommons.WithId
import baile.domain.images.{ AlbumLabelMode, Picture, PictureTag, PictureTagArea }
import baile.services.images.util.ImagesRandomGenerator._
import baile.services.remotestorage.RemoteStorageService
import baile.RandomGenerators._
import baile.services.images.ImagesCommonService.AugmentationResultImage
import cortex.api.job.album.augmentation.{
  AppliedAugmentation,
  AppliedBlurringParams,
  AppliedCroppingParams,
  AppliedNoisingParams,
  AppliedOcclusionParams,
  AppliedPhotometricDistortParams,
  AppliedRotationParams,
  AppliedShearingParams,
  AppliedTranslationParams,
  AppliedZoomInParams,
  AppliedZoomOutParams,
  OcclusionMode,
  TranslationMode
}
import cortex.api.job.album.augmentation.AppliedAugmentation.GeneralParams
import cortex.api.job.album.common.{ Image, Tag, TagArea, TaggedImage }
import cortex.api.job.computervision.PredictedTag

class ImagesCommonServiceSpec extends ExtendedBaseSpec {

  trait Setup {

    val pictureDao = mock[PictureDao]
    val albumDao = mock[AlbumDao]
    val storageService = mock[RemoteStorageService]

    val service = new ImagesCommonService(
      pictureDao,
      albumDao,
      storageService,
      500,
      conf.getString("album.storage-prefix")
    )

    val album = randomAlbum()
    val userId = UUID.randomUUID()

    val pictures = List(
      WithId(
        Picture(
          albumId = "a1",
          filePath = "path1",
          fileName = "name1",
          fileSize = Some(42l),
          caption = None,
          predictedCaption = None,
          tags = Seq(PictureTag(
            label = "tag1",
            area = Some(PictureTagArea(
              top = 10,
              left = 20,
              height = 40,
              width = 60
            ))
          )),
          predictedTags = Seq(PictureTag(
            label = "tag1",
            area = Some(PictureTagArea(
              top = 10,
              left = 20,
              height = 40,
              width = 60
            )),
            confidence = Some(0.42)
          )),
          meta = Map.empty,
          originalPictureId = None,
          appliedAugmentations = None
        ),
        randomString()
      ),
      WithId(
        Picture(
          albumId = "a2",
          filePath = "path2",
          fileName = "name2",
          fileSize = Some(42l),
          caption = None,
          predictedCaption = None,
          tags = Seq(PictureTag(
            label = "tag1",
            area = Some(PictureTagArea(
              top = 10,
              left = 20,
              height = 40,
              width = 60
            ))
          )),
          predictedTags = Seq(PictureTag(
            label = "tag1",
            area = Some(PictureTagArea(
              top = 10,
              left = 20,
              height = 40,
              width = 60
            )),
            confidence = Some(0.42)
          )),
          meta = Map.empty,
          originalPictureId = None,
          appliedAugmentations = None
        ),
        randomString()
      )
    )
  }

  "ImagesCommonService#getAlbum" should {

    "return album" in new Setup {
      albumDao.get(album.id) shouldReturn future(Some(album))

      service.getAlbum(album.id).futureValue shouldBe Some(album)
    }

  }

  "ImagesCommonService#getAlbumMandatory" should {

    "return album" in new Setup {
      albumDao.get(album.id) shouldReturn future(Some(album))

      service.getAlbumMandatory(album.id).futureValue shouldBe album
    }

    "raise exception" in new Setup {
      albumDao.get(album.id) shouldReturn future(None)

      service.getAlbumMandatory(album.id).failed.futureValue should not be a[NullPointerException]
    }

  }

  "ImagesCommonService#countUserAlbums" should {

    "return number of albums" in new Setup {
      albumDao.count(*) shouldReturn future(1)

      service.countUserAlbums("name", None, userId).futureValue shouldBe 1
    }

  }

  "ImagesCommonService#getImagesS3Prefix" should {

    "return images prefix for album" in new Setup {
      storageService.path(*, album.entity.picturesPrefix) shouldReturn "prefix"

      service.getImagesPathPrefix(album.entity) shouldBe "prefix"
    }

  }

  "ImagesCommonService#getPictures" should {

    "return pictures of album" in new Setup {
      pictureDao.listAll(*) shouldReturn future(Seq.empty)

      service.getPictures(album.id, false).futureValue shouldBe Seq.empty
    }

  }

  "ImagesCommonService#convertCortexTagToPictureTag" should {

    "convert cortex tag to picture tag" in new Setup {
      val tag = PredictedTag(
        tag = Some(Tag(
          label = "tag1",
          area = Some(TagArea(
            top = 10,
            left = 20,
            height = 40,
            width = 60
          ))
        )),
        confidence = 0.42
      )

      service.convertCortexTagToPictureTag(tag, AlbumLabelMode.Localization).success.value shouldBe PictureTag(
        label = "tag1",
        area = Some(PictureTagArea(
          top = 10,
          left = 20,
          height = 40,
          width = 60
        )),
        confidence = Some(0.42)
      )
    }

  }

  "ImagesCommonService#convertPicturesToCortexTaggedImages" should {

    "convert picture to cortex tagged images" in new Setup {
      service.convertPicturesToCortexTaggedImages(pictures) shouldBe pictures.map { picture =>
        TaggedImage(
          image = Some(Image(
            filePath = picture.entity.filePath,
            referenceId = Some(picture.id),
            fileSize = picture.entity.fileSize,
            displayName = Some(picture.entity.fileName)
          )),
          tags = picture.entity.tags.map { tag =>
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
        )
      }
    }

  }

  "ImagesCommonService#populateAugmentedAlbum" should {

    "populate augmented album" in new Setup {
      val inputAlbumId = randomString()
      val outputAlbumId = randomString()

      def createAppliedAugmentation(params: GeneralParams): AppliedAugmentation =
        AppliedAugmentation(generalParams = params)

      val appliedAugmentations = List(
        createAppliedAugmentation(GeneralParams.RotationParams(AppliedRotationParams(0.4f, true))),
        createAppliedAugmentation(GeneralParams.ShearingParams(AppliedShearingParams(0.4f, true))),
        createAppliedAugmentation(GeneralParams.NoisingParams(AppliedNoisingParams(2))),
        createAppliedAugmentation(GeneralParams.ZoomInParams(AppliedZoomInParams(20, true))),
        createAppliedAugmentation(GeneralParams.ZoomOutParams(AppliedZoomOutParams(0.2f, true))),
        createAppliedAugmentation(GeneralParams.OcclusionParams(
          AppliedOcclusionParams(0.4f, OcclusionMode.ZERO, true, 6)
        )),
        createAppliedAugmentation(GeneralParams.TranslationParams(
          AppliedTranslationParams(0.4f, TranslationMode.CONSTANT, false)
        )),
        createAppliedAugmentation(GeneralParams.PhotometricDistortParams(
          AppliedPhotometricDistortParams(0.4f, 0.2f, 0.6f, 0.9f)
        )),
        createAppliedAugmentation(GeneralParams.CroppingParams(AppliedCroppingParams(0.4f, true))),
        createAppliedAugmentation(GeneralParams.BlurringParams(AppliedBlurringParams(0.4f)))
      )

      val resultImages = pictures.map { picture =>
        AugmentationResultImage(
          image = TaggedImage(
            image = Some(Image(
              filePath = picture.entity.filePath,
              referenceId = Some(picture.id),
              fileSize = picture.entity.fileSize
            ))
          ),
          fileSize = picture.entity.fileSize,
          appliedAugmentations = appliedAugmentations
        )
      }

      pictureDao.listAll(*) shouldReturn future(pictures)
      pictureDao.createMany(*) shouldReturn future(Seq.empty)

      service.populateAugmentedAlbum(
        inputAlbumId = inputAlbumId,
        outputAlbumId = outputAlbumId,
        labelMode = AlbumLabelMode.Localization,
        resultImages
      ).futureValue
    }

  }

}
