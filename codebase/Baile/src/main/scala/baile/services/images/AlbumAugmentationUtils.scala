package baile.services.images

import baile.domain.images.augmentation._
import cats.implicits._
import cortex.api.job.album.augmentation
import cortex.api.job.album.augmentation.RequestedAugmentation

private[services] object AlbumAugmentationUtils {

  def convertToCortexRequestedAugmentations(
    augmentationParams: Seq[AugmentationParams]
  ): Seq[RequestedAugmentation] = {
    augmentationParams map { request =>
      RequestedAugmentation(
        bloatFactor = request.bloatFactor,
        params = convertToCortexAugmentationRequestParams(request)
      )
    }
  }

  def validateAugmentationRequestParams[T](
    augmentationRequestParams: AugmentationParams,
    errorBuilder: String => T
  ): Either[T, Unit] = {
    val validations = augmentationRequestParams match {
      case RotationParams(angles, _, _) => List(
        (
          angles.forall(angle => angle > 0 && angle < 360),
          "Invalid angle. Angles must be between 0 and 360"
        )
      )
      case ShearingParams(angles, _, _) => List(
        (
          angles.forall(angle => angle > 0 && angle <= 60),
          "Invalid angle. Angles must be greater than 0 and less than or equals to 60"
        )
      )
      case NoisingParams(noiseSignalRatios, _) => List(
        (
          noiseSignalRatios.forall(_ > 0),
          "Invalid ratio. Ratios must be greater than 0"
        )
      )
      case ZoomInParams(magnifications, _, _) => List(
        (
          magnifications.forall(_ >= 1),
          "Invalid magnification factor. Magnification factors should be greater than or equals to 1"
        )
      )
      case ZoomOutParams(shrinkFactors, _, _) => List(
        (
          shrinkFactors.forall(factor => factor > 0 && factor <= 1),
          "Invalid shrinking factor. Shrinking factors should be greater than 0 and less than or equals to 1"
        )
      )
      case OcclusionParams(occAreaFractions, _, _, tarWinSize, _) => List(
        (
          occAreaFractions.forall(fraction => fraction >= 0 && fraction < 1),
          "Invalid area fraction. Area fractions should be greater than or equals to 0 and less than 1"
        ),
        (
          tarWinSize >= 10,
          "Invalid target window size. It should be greater than equals to 10"
        )
      )
      case TranslationParams(translateFractions, _, _, _) => List(
        (
          translateFractions.forall(fraction => fraction >= 0 && fraction < 0.5),
          "Invalid translate fraction. Translate fractions should be greater than or equal to 0 and less than 0.5"
        )
      )
      case SaltPepperParams(knockoutFactors, pepperProbability, _) => List(
        (
          knockoutFactors.forall(factor => factor >= 0 && factor <= 1),
          "Invalid knockout factor. Knockout factors should be should be between 0 to 1 (inclusive)"
        ),
        (
          pepperProbability >= 0 && pepperProbability <= 1,
          "Invalid pepper probability. It should be between 0 to 1 (inclusive)"
        )
      )
      case CroppingParams(cropAreaFractions, cropsPerImage, _, _) => List(
        (
          cropAreaFractions.forall(fraction => fraction >= 0 && fraction <= 1),
          "Invalid crop area fraction. Crop area fractions should be between 0 to 1 (inclusive)"
        ),
        (
          cropsPerImage >= 1,
          "Invalid crops per image param. It should be greater than equals to 1"
        )
      )
      case BlurringParams(sigmaList, _) => List(
        (
          sigmaList.forall(_ > 0),
          "Invalid sigma. Sigmas should be greater than 0"
        )
      )
      case PhotometricDistortParams(alphaBounds, deltaMax, _) => List(
        (
          alphaBounds.min > 0 && alphaBounds.max > alphaBounds.min,
          "Invalid alpha bounds. Min value should be greater than 0 and Max should be greater than Min"
        ),
        (
          deltaMax.abs < 360,
          "Invalid delta max value provided. It should be between -360 and 360"
        )
      )
      case _ => List.empty
    }

    validations.foldLeft(().asRight[T]) { case (soFar, (predicate, errorMessage)) =>
      soFar.ensure(errorBuilder(errorMessage))(_ => predicate)
    }
  }

  private def convertToCortexAugmentationRequestParams(
    augmentationRequestParams: AugmentationParams
  ): RequestedAugmentation.Params = augmentationRequestParams match {
    case RotationParams(angles, resize, _) =>
      RequestedAugmentation.Params.RotationParams(
        augmentation.RotationRequestParams(
          angles = angles,
          resize = resize
        )
      )
    case ShearingParams(angles, resize, _) =>
      RequestedAugmentation.Params.ShearingParams(
        augmentation.ShearingRequestParams(
          angles = angles,
          resize = resize
        )
      )
    case NoisingParams(noiseSignalRatios, _) =>
      RequestedAugmentation.Params.NoisingParams(
        augmentation.NoisingRequestParams(
          noiseSignalRatios = noiseSignalRatios
        )
      )
    case ZoomInParams(magnifications, resize, _) =>
      RequestedAugmentation.Params.ZoomInParams(
        augmentation.ZoomInRequestParams(
          magnifications = magnifications,
          resize = resize
        )
      )
    case ZoomOutParams(shrinkFactors, resize, _) =>
      RequestedAugmentation.Params.ZoomOutParams(
        augmentation.ZoomOutRequestParams(
          shrinkFactors = shrinkFactors,
          resize = resize
        )
      )
    case OcclusionParams(occAreaFractions, mode, isSarAlbum, tarWinSize, _) =>
      RequestedAugmentation.Params.OcclusionParams(
        augmentation.OcclusionRequestParams(
          occAreaFractions = occAreaFractions,
          mode = mode match {
            case OcclusionMode.Background => augmentation.OcclusionMode.BACKGROUND
            case OcclusionMode.Zero => augmentation.OcclusionMode.ZERO
          },
          isSarAlbum = isSarAlbum,
          tarWinSize = tarWinSize
        )
      )
    case TranslationParams(translateFractions, mode, resize, _) =>
      RequestedAugmentation.Params.TranslationParams(
        augmentation.TranslationRequestParams(
          translateFractions = translateFractions,
          mode = mode match {
            case TranslationMode.Constant => augmentation.TranslationMode.CONSTANT
            case TranslationMode.Reflect => augmentation.TranslationMode.REFLECT
          },
          resize = resize
        )
      )
    case SaltPepperParams(knockoutFractions, pepperProbability, _) =>
      RequestedAugmentation.Params.SaltPepperParams(
        augmentation.SaltPepperRequestParams(
          knockoutFractions = knockoutFractions,
          pepperProbability = pepperProbability
        )
      )
    case MirroringParams(axesToFlip, _) =>
      RequestedAugmentation.Params.MirroringParams(
        augmentation.MirroringRequestParams(
          axesToFlip = axesToFlip map {
            case MirroringAxisToFlip.Horizontal => augmentation.MirroringAxisToFlip.HORIZONTAL
            case MirroringAxisToFlip.Vertical => augmentation.MirroringAxisToFlip.VERTICAL
            case MirroringAxisToFlip.Both => augmentation.MirroringAxisToFlip.BOTH
          }
        )
      )
    case CroppingParams(cropAreaFractions, cropsPerImage, resize, _) =>
      RequestedAugmentation.Params.CroppingParams(
        augmentation.CroppingRequestParams(
          cropAreaFractions = cropAreaFractions,
          cropsPerImage = cropsPerImage,
          resize = resize
        )
      )
    case BlurringParams(sigmaList, _) =>
      RequestedAugmentation.Params.BlurringParams(
        augmentation.BlurringRequestParams(
          sigmaList = sigmaList
        )
      )
    case PhotometricDistortParams(alphaBounds, deltaMax, _) =>
      RequestedAugmentation.Params.PhotometricDistortParams(
        augmentation.PhotometricDistortRequestParams(
          alphaBounds = Some(augmentation.PhotometricDistortAlphaBounds(
            min = alphaBounds.min,
            max = alphaBounds.max
          )),
          deltaMax = deltaMax
        )
      )
  }

}
