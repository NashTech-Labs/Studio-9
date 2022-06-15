package baile.routes.contract.images

import baile.domain.images.augmentation.{ AugmentationType, OcclusionMode, TranslationMode }
import baile.utils.json.EnumFormatBuilder
import play.api.libs.json._

package object augmentation {

  implicit val AugmentationTypeFormat: Format[AugmentationType] = EnumFormatBuilder.build(
    {
      case "TRANSLATION" => AugmentationType.Translation
      case "ROTATION" => AugmentationType.Rotation
      case "SHEARING" => AugmentationType.Shearing
      case "NOISING" => AugmentationType.Noising
      case "ZOOM_IN" => AugmentationType.ZoomIn
      case "ZOOM_OUT" => AugmentationType.ZoomOut
      case "OCCLUSION" => AugmentationType.Occlusion
      case "SALT_PEPPER" => AugmentationType.SaltPepper
      case "MIRRORING" => AugmentationType.Mirroring
      case "CROPPING" => AugmentationType.Cropping
      case "BLURRING" => AugmentationType.Blurring
      case "PHOTO_DISTORT" => AugmentationType.PhotoDistort
    },
    {
      case AugmentationType.Translation => "TRANSLATION"
      case AugmentationType.Rotation => "ROTATION"
      case AugmentationType.Shearing => "SHEARING"
      case AugmentationType.Noising => "NOISING"
      case AugmentationType.ZoomIn => "ZOOM_IN"
      case AugmentationType.ZoomOut => "ZOOM_OUT"
      case AugmentationType.Occlusion => "OCCLUSION"
      case AugmentationType.SaltPepper => "SALT_PEPPER"
      case AugmentationType.Mirroring => "MIRRORING"
      case AugmentationType.Cropping => "CROPPING"
      case AugmentationType.Blurring => "BLURRING"
      case AugmentationType.PhotoDistort => "PHOTO_DISTORT"
    },
    "augmentation type"
  )

  implicit val OcclusionModeFormat: Format[OcclusionMode] = EnumFormatBuilder.build(
    {
      case "ZERO" => OcclusionMode.Zero
      case "BACKGROUND" => OcclusionMode.Background
    },
    {
      case OcclusionMode.Zero => "ZERO"
      case OcclusionMode.Background => "BACKGROUND"
    },
    "occlusion mode"
  )

  implicit val TranslationModeFormat: Format[TranslationMode] = EnumFormatBuilder.build(
    {
      case "REFLECT" => TranslationMode.Reflect
      case "CONSTANT" => TranslationMode.Constant
    },
    {
      case TranslationMode.Reflect => "REFLECT"
      case TranslationMode.Constant => "CONSTANT"
    },
    "translation mode"
  )

}
