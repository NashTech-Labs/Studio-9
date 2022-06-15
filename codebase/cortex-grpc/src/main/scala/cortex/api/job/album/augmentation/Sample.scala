package cortex.api.job.album.augmentation

import java.util.UUID

import cortex.api.job.album.common.{ Image, TaggedImage, Tag }

import scala.util.Random

// scalastyle:off
object Sample {

  val filePathPrefix = s"baile/data/albums${UUID.randomUUID}"

  val images = Seq(
    TaggedImage(Some(Image("https://deepcortex.ai/pic1.png", Some(UUID.randomUUID().toString))), Seq(Tag("label1"))),
    TaggedImage(Some(Image("https://deepcortex.ai/pic2.png", Some(UUID.randomUUID().toString))), Seq(Tag("label1"))),
    TaggedImage(Some(Image("https://deepcortex.ai/pic3.png", Some(UUID.randomUUID().toString))), Seq(Tag("label2"))),
    TaggedImage(Some(Image("https://deepcortex.ai/pic4.png", Some(UUID.randomUUID().toString))), Seq(Tag("label2")))
  )

  val rotationRequestParams = RequestedAugmentation.Params.RotationParams(
    RotationRequestParams(
      angles = Seq(10, 30, 65, 110),
      resize = false
    )
  )

  val shearingRequestParams = RequestedAugmentation.Params.ShearingParams(
    ShearingRequestParams(
      angles = Seq(30, 50.5F),
      resize = true
    )
  )

  val noisingRequestParams = RequestedAugmentation.Params.NoisingParams(
    NoisingRequestParams(
      noiseSignalRatios = Seq(10.5F, 90)
    )
  )

  val zoomInRequestParams = RequestedAugmentation.Params.ZoomInParams(
    ZoomInRequestParams(
      magnifications = Seq(3, 4, 6),
      resize = true
    )
  )

  val zoomOutRequestParams = RequestedAugmentation.Params.ZoomOutParams(
    ZoomOutRequestParams(
      shrinkFactors = Seq(0.2F, 0.6F),
      resize = false
    )
  )

  val occlusionRequestParams = RequestedAugmentation.Params.OcclusionParams(
    OcclusionRequestParams(
      occAreaFractions = Seq(0.03F, 0.6F),
      mode = OcclusionMode.BACKGROUND,
      isSarAlbum = false,
      tarWinSize = 2
    )
  )

  val translationRequestParams = RequestedAugmentation.Params.TranslationParams(
    TranslationRequestParams(
      translateFractions = Seq(0.1F, 0.2F, 0.3F),
      mode = TranslationMode.REFLECT,
      resize = true
    )
  )

  val saltPepperRequestParams = RequestedAugmentation.Params.SaltPepperParams(
    SaltPepperRequestParams(
      knockoutFractions = Seq(0.08F, 0.9F),
      pepperProbability = 0.3F
    )
  )

  val mirroringRequestParams = RequestedAugmentation.Params.MirroringParams(
    MirroringRequestParams(
      axesToFlip = Seq(MirroringAxisToFlip.HORIZONTAL, MirroringAxisToFlip.VERTICAL, MirroringAxisToFlip.VERTICAL)
    )
  )

  val croppingRequestParams = RequestedAugmentation.Params.CroppingParams(
    CroppingRequestParams(
      cropAreaFractions = Seq(0.24F, 0.84F),
      cropsPerImage = 2,
      resize = false
    )
  )

  val blurringRequestParams = RequestedAugmentation.Params.BlurringParams(
    BlurringRequestParams(
      sigmaList = Seq(0.5F, 3.4F, 4)
    )
  )

  val photometricDistortRequestParams = RequestedAugmentation.Params.PhotometricDistortParams(
    PhotometricDistortRequestParams(
      alphaBounds = Some(PhotometricDistortAlphaBounds(1.2F, 3.4F)),
      deltaMax = 39.0F
    )
  )

  val augmentations = Seq(
    rotationRequestParams,
    shearingRequestParams,
    noisingRequestParams,
    zoomInRequestParams,
    zoomOutRequestParams,
    occlusionRequestParams,
    translationRequestParams,
    saltPepperRequestParams,
    mirroringRequestParams,
    croppingRequestParams,
    blurringRequestParams,
    photometricDistortRequestParams
  ).map { params =>
    RequestedAugmentation(
      bloatFactor = Random.nextInt(10) + 1,
      params = params
    )
  }

  val request = AugmentationRequest(
    images = images,
    filePathPrefix = filePathPrefix,
    augmentations = augmentations,
    bloatFactor = Some(4),
    targetPrefix = s"baile/data/albums/${UUID.randomUUID.toString}",
    includeOriginalImages = true
  )

  val appliedRotationParams = AppliedAugmentation.GeneralParams.RotationParams(
    AppliedRotationParams(angle = 10)
  )

  val appliedShearingParams = AppliedAugmentation.GeneralParams.ShearingParams(
    AppliedShearingParams(
      angle = 50.5F,
      resize = true
    )
  )

  val appliedNoisingParams = AppliedAugmentation.GeneralParams.NoisingParams(
    AppliedNoisingParams(noiseSignalRatio = 10.5F)
  )

  val appliedZoomInParams = AppliedAugmentation.GeneralParams.ZoomInParams(
    AppliedZoomInParams(
      magnification = 6,
      resize = true
    )
  )

  val appliedZoomOutParams = AppliedAugmentation.GeneralParams.ZoomOutParams(
    AppliedZoomOutParams(
      shrinkFactor = 0.2F,
      resize = false
    )
  )

  val appliedOcclusionParams = AppliedAugmentation.GeneralParams.OcclusionParams(
    AppliedOcclusionParams(
      occAreaFraction = 0.03F,
      mode = OcclusionMode.BACKGROUND,
      isSarAlbum = false,
      tarWinSize = 2
    )
  )

  val appliedTranslationParams = AppliedAugmentation.GeneralParams.TranslationParams(
    AppliedTranslationParams(
      translateFraction = 0.2F,
      mode = TranslationMode.REFLECT,
      resize = true
    )
  )

  val appliedSaltPepperParams = AppliedAugmentation.GeneralParams.SaltPepperParams(
    AppliedSaltPepperParams(
      knockoutFraction = 0.08F,
      pepperProbability = 0.3F
    )
  )

  val appliedMirroringParams = AppliedAugmentation.GeneralParams.MirroringParams(
    AppliedMirroringParams(axisFlipped = MirroringAxisToFlip.HORIZONTAL)
  )

  val appliedCroppingParams = AppliedAugmentation.GeneralParams.CroppingParams(
    AppliedCroppingParams(
      cropAreaFraction = 0.24F,
      resize = false
    )
  )

  val appliedBlurringParams = AppliedAugmentation.GeneralParams.BlurringParams(
    AppliedBlurringParams(sigma = 3.4F)
  )

  val appliedPhotometricDistortParams = AppliedAugmentation.GeneralParams.PhotometricDistortParams(
    AppliedPhotometricDistortParams(
      deltaMax = 39.0F,
      alphaConstant = 0.1F,
      alphaSaturation = 0.94F,
      deltaHue = 0.48F
    )
  )

  val extraParams = Map(
    "param1" -> 12f,
    "param2" -> 13f
  )

  val internalParams = Map(
    "internalParam1" -> 113f,
    "internalParam2" -> 119f
  )

  val response = AugmentationResult(
    originalImages = images,
    augmentedImages = images.map { image =>
      AugmentedImage(
        image = Some(image),
        augmentations = Seq(
          AppliedAugmentation(
            generalParams = appliedBlurringParams,
            extraParams = extraParams,
            internalParams = internalParams
          ),
          AppliedAugmentation(
            generalParams = appliedPhotometricDistortParams,
            extraParams = extraParams,
            internalParams = internalParams
          )
        )
      )
    },
    dataFetchTime = 1000L,
    augmentationTime = 1000L,
    pipelineTimings = Map(
      "step1" -> 1000L,
      "step2" -> 1000L,
      "step3" -> 1000L,
      "step4" -> 1000L
    )
  )

  val appliedAugmentationSummaryCellCroppingParams = RequestedAugmentation(
    bloatFactor = 1,
    params = croppingRequestParams
  )

  val augmentationSummaryCell: AugmentationSummaryCell = AugmentationSummaryCell(
    imagesCount = 100,
    requestedAugmentation = Some(appliedAugmentationSummaryCellCroppingParams)
  )

  val augmentationSummary = AugmentationSummary(Seq(augmentationSummaryCell))

}
