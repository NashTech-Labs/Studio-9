package cortex.jobmaster.orion.service.domain

import cortex.api.job.album.common.{ Image, Tag, TagArea, TaggedImage }
import cortex.api.job.album.augmentation._
import cortex.task.data_augmentation.DataAugmentationParams
import cortex.task.data_augmentation.DataAugmentationParams.AugmentationType

trait WithAugmentation {

  protected def parseAugmentationResult(result: DataAugmentationParams.TransformImagesResult): Seq[AugmentedImage] = {
    val localAugmentedImg = (
      result.imagePaths zip result.tags zip result.augmentations zip result.imageSizes zip result.referenceIds
    ).map {
        case ((((path, tags), aug), size), refId) => (path, tags, aug, size, refId)
      }

    localAugmentedImg.map {
      case (path, tags, aug, size, refId) =>
        AugmentedImage(
          image         = Some(TaggedImage(
            Some(Image(path, Some(refId))),
            tags.map(t => fromLocalTag(t))
          )),
          augmentations = fromLocalAugmentation(aug),
          fileSize      = size
        )
    }
  }

  protected def toLocalDAParams(
    requestedAugmentation: RequestedAugmentation
  ): DataAugmentationParams.AugmentationParams = {
    import RequestedAugmentation.Params

    val bloatFactor = requestedAugmentation.bloatFactor

    requestedAugmentation.params match {
      case params: Params.RotationParams =>
        DataAugmentationParams.Rotation(
          angles      = params.value.angles,
          resize      = params.value.resize,
          bloatFactor = bloatFactor
        )

      case params: Params.ShearingParams =>
        DataAugmentationParams.Shearing(
          angles      = params.value.angles,
          resize      = params.value.resize,
          bloatFactor = bloatFactor
        )

      case params: Params.NoisingParams =>
        DataAugmentationParams.Noising(
          noiseSignalRatios = params.value.noiseSignalRatios,
          bloatFactor       = bloatFactor
        )

      case params: Params.ZoomInParams =>
        DataAugmentationParams.ZoomIn(
          magnifications = params.value.magnifications,
          resize         = params.value.resize,
          bloatFactor    = bloatFactor
        )

      case params: Params.ZoomOutParams =>
        DataAugmentationParams.ZoomOut(
          shrinkFactors = params.value.shrinkFactors,
          resize        = params.value.resize,
          bloatFactor   = bloatFactor
        )

      case params: Params.OcclusionParams =>
        DataAugmentationParams.Occlusion(
          occAreaFractions = params.value.occAreaFractions,
          mode             = params.value.mode.name.toLowerCase,
          isSarAlbum       = params.value.isSarAlbum,
          tarWinSize       = params.value.tarWinSize,
          bloatFactor      = bloatFactor
        )

      case params: Params.TranslationParams =>
        DataAugmentationParams.Translation(
          translateFractions = params.value.translateFractions,
          mode               = params.value.mode.name.toLowerCase,
          resize             = params.value.resize,
          bloatFactor        = bloatFactor
        )

      case params: Params.SaltPepperParams =>
        DataAugmentationParams.SaltPepper(
          knockoutFractions = params.value.knockoutFractions,
          pepperProbability = params.value.pepperProbability,
          bloatFactor       = bloatFactor
        )

      case params: Params.MirroringParams =>
        DataAugmentationParams.Mirroring(
          axesToFlip  = params.value.axesToFlip.map(_.name.toLowerCase),
          bloatFactor = bloatFactor
        )

      case params: Params.CroppingParams =>
        DataAugmentationParams.Cropping(
          cropAreaFractions = params.value.cropAreaFractions,
          cropsPerImage     = params.value.cropsPerImage,
          resize            = params.value.resize,
          bloatFactor       = bloatFactor
        )

      case params: Params.PhotometricDistortParams =>
        DataAugmentationParams.PhotometricDistort(
          min         = params.value.getAlphaBounds.min,
          max         = params.value.getAlphaBounds.max,
          deltaMax    = params.value.deltaMax,
          bloatFactor = bloatFactor
        )

      case params: Params.BlurringParams =>
        DataAugmentationParams.Blurring(params.value.sigmaList, bloatFactor)

      case Params.Empty =>
        throw new RuntimeException("Requested augmentation cannot be empty")
    }
  }

  private def fromLocalTag(t: DataAugmentationParams.Tag): Tag = {
    t match {
      case DataAugmentationParams.Tag(label, Some(xMin), Some(yMin), Some(xMax), Some(yMax)) =>
        Tag(
          label = label,
          Some(TagArea(
            top    = yMin,
            left   = xMin,
            height = yMax - yMin,
            width  = xMax - xMin
          ))
        )
      case DataAugmentationParams.Tag(label, _, _, _, _) =>
        Tag(label)
    }
  }

  private def fromLocalAugmentation(augInfo: DataAugmentationParams.AppliedAugmentationInfo): Seq[AppliedAugmentation] = {
    if (augInfo.name == AugmentationType.Unchanged) {
      Seq()
    } else {
      Seq(toAppliedAugmentation(augInfo)) // DA library supports only one aug per image at a time
    }
  }

  private def toAppliedAugmentation(info: DataAugmentationParams.AppliedAugmentationInfo): AppliedAugmentation = {
    def occlusionMode(mode: String): OcclusionMode = {
      mode match {
        case "zero"       => OcclusionMode.ZERO
        case "background" => OcclusionMode.BACKGROUND
        case _            => throw new RuntimeException(s"Unknown occlusion mode: $mode")
      }
    }

    def translationMode(mode: String): TranslationMode = {
      mode match {
        case "reflect"  => TranslationMode.REFLECT
        case "constant" => TranslationMode.CONSTANT
        case _          => throw new RuntimeException(s"Unknown translation mode: $mode")
      }
    }

    def mirroringAxis(axis: Int): MirroringAxisToFlip = {
      axis match {
        case 0 => MirroringAxisToFlip.HORIZONTAL
        case 1 => MirroringAxisToFlip.VERTICAL
        case 2 => MirroringAxisToFlip.BOTH
        case _ => throw new RuntimeException(s"Unknown axis: $axis")
      }
    }

    info.name match {
      case AugmentationType.Rotation => AppliedAugmentation(
        generalParams = AppliedAugmentation.GeneralParams.RotationParams(
          AppliedRotationParams(angle  = info.arg, resize = info.resize.getOrElse(false))
        )
      )
      case AugmentationType.Shearing => AppliedAugmentation(
        generalParams = AppliedAugmentation.GeneralParams.ShearingParams(
          AppliedShearingParams(angle  = info.arg, resize = info.resize.getOrElse(false))
        )
      )
      case AugmentationType.Noising => AppliedAugmentation(
        generalParams = AppliedAugmentation.GeneralParams.NoisingParams(
          AppliedNoisingParams(noiseSignalRatio = info.arg)
        )
      )
      case AugmentationType.ZoomIn => AppliedAugmentation(
        generalParams = AppliedAugmentation.GeneralParams.ZoomInParams(
          AppliedZoomInParams(magnification = info.arg, resize = info.resize.getOrElse(false))
        )
      )
      case AugmentationType.ZoomOut => AppliedAugmentation(
        generalParams = AppliedAugmentation.GeneralParams.ZoomOutParams(
          AppliedZoomOutParams(shrinkFactor = info.arg, resize = info.resize.getOrElse(false))
        )
      )
      case AugmentationType.Occlusion => AppliedAugmentation(
        generalParams = AppliedAugmentation.GeneralParams.OcclusionParams(
          AppliedOcclusionParams(
            occAreaFraction = info.arg,
            mode            = occlusionMode(info.mode.getOrElse(throw new RuntimeException("no mode"))),
            isSarAlbum      = info.isSarAlbum.getOrElse(throw new RuntimeException("no isSarAlbum")),
            tarWinSize      = info.tarWinSize.getOrElse(throw new RuntimeException("no tarWinSize"))
          )
        )
      )
      case AugmentationType.Translation => AppliedAugmentation(
        generalParams = AppliedAugmentation.GeneralParams.TranslationParams(
          AppliedTranslationParams(
            translateFraction = info.arg,
            mode              = translationMode(info.mode.getOrElse(throw new RuntimeException("no mode"))),
            resize            = info.resize.getOrElse(false)
          )
        )
      )
      case AugmentationType.SaltPepper => AppliedAugmentation(
        generalParams = AppliedAugmentation.GeneralParams.SaltPepperParams(
          AppliedSaltPepperParams(
            knockoutFraction  = info.arg,
            pepperProbability = info.pepperProbability.getOrElse(throw new RuntimeException("no pepperProbability"))
          )
        )
      )
      case AugmentationType.Mirroring => AppliedAugmentation(
        generalParams = AppliedAugmentation.GeneralParams.MirroringParams(
          AppliedMirroringParams(axisFlipped = mirroringAxis(info.arg.toInt))
        )
      )
      case AugmentationType.Cropping => AppliedAugmentation(
        generalParams = AppliedAugmentation.GeneralParams.CroppingParams(
          AppliedCroppingParams(cropAreaFraction = info.arg, resize = info.resize.getOrElse(false))
        )
      )
      case AugmentationType.PhotometricDistort => AppliedAugmentation(
        generalParams = AppliedAugmentation.GeneralParams.PhotometricDistortParams(
          AppliedPhotometricDistortParams(
            deltaMax        = info.arg,
            alphaConstant   = info.alphaContrast.getOrElse(throw new RuntimeException("no alphaConstant")),
            alphaSaturation = info.alphaSaturation.getOrElse(throw new RuntimeException("no alphaSaturation")),
            deltaHue        = info.deltaHue.getOrElse(throw new RuntimeException("no deltaHue"))
          )
        )
      )
      case AugmentationType.Blurring => AppliedAugmentation(
        generalParams = AppliedAugmentation.GeneralParams.BlurringParams(
          AppliedBlurringParams(sigma = info.arg)
        )
      )
      case AugmentationType.Unchanged => {
        throw new RuntimeException("Unchanged cannot be translated to AppliedAugmentation")
      }
    }
  }

}
