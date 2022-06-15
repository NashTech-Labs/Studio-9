package baile.routes.contract.images.augmentation

import baile.domain.images.augmentation._
import play.api.libs.json._

trait AlbumAugmentationStep {
  val augmentationType: AugmentationType
  val bloatFactor: Int

  def toDomain: AugmentationParams
}

object AlbumAugmentationStep {

  def fromDomain(augmentationParams: AugmentationParams): AlbumAugmentationStep = {
    augmentationParams match {
      case params: RotationParams =>
        AlbumAugmentationRotation.fromDomain(params)
      case params: ShearingParams =>
        AlbumAugmentationShearing.fromDomain(params)
      case params: NoisingParams =>
        AlbumAugmentationNoising.fromDomain(params)
      case params: CroppingParams =>
        AlbumAugmentationCropping.fromDomain(params)
      case params: TranslationParams =>
        AlbumAugmentationTranslation.fromDomain(params)
      case params: ZoomInParams =>
        AlbumAugmentationZoomIn.fromDomain(params)
      case params: ZoomOutParams =>
        AlbumAugmentationZoomOut.fromDomain(params)
      case params: SaltPepperParams =>
        AlbumAugmentationSaltPepper.fromDomain(params)
      case params: OcclusionParams =>
        AlbumAugmentationOcclusion.fromDomain(params)
      case params: BlurringParams =>
        AlbumAugmentationBlurring.fromDomain(params)
      case params: MirroringParams =>
        AlbumAugmentationMirroring.fromDomain(params)
      case params: PhotometricDistortParams =>
        AlbumAugmentationPhotoDistort.fromDomain(params)
    }
  }

  implicit val AlbumAugmentationStepReads: Reads[AlbumAugmentationStep] =
    new Reads[AlbumAugmentationStep] {

      private def readByType(augmentationType: JsString, json: JsValue): JsResult[AlbumAugmentationStep] =
        AugmentationTypeFormat.reads(augmentationType).flatMap {
          case AugmentationType.PhotoDistort =>
            AlbumAugmentationPhotoDistort.AlbumAugmentationPhotoDistortFormat.reads(json)
          case AugmentationType.Rotation =>
            AlbumAugmentationRotation.AlbumAugmentationRotationFormat.reads(json)
          case AugmentationType.Shearing =>
            AlbumAugmentationShearing.AlbumAugmentationShearingFormat.reads(json)
          case AugmentationType.Cropping =>
            AlbumAugmentationCropping.AlbumAugmentationCroppingFormat.reads(json)
          case AugmentationType.Blurring =>
            AlbumAugmentationBlurring.AlbumAugmentationBlurringFormat.reads(json)
          case AugmentationType.Translation =>
            AlbumAugmentationTranslation.AlbumAugmentationTranslationFormat.reads(json)
          case AugmentationType.Occlusion =>
            AlbumAugmentationOcclusion.AlbumAugmentationOcclusionFormat.reads(json)
          case AugmentationType.SaltPepper =>
            AlbumAugmentationSaltPepper.AlbumAugmentationSaltPepperFormat.reads(json)
          case AugmentationType.Noising =>
            AlbumAugmentationNoising.AlbumAugmentationNoisingFormat.reads(json)
          case AugmentationType.Mirroring =>
            AlbumAugmentationMirroring.AlbumAugmentationMirroringFormat.reads(json)
          case AugmentationType.ZoomIn =>
            AlbumAugmentationZoomIn.AlbumAugmentationZoomInFormat.reads(json)
          case AugmentationType.ZoomOut =>
            AlbumAugmentationZoomOut.AlbumAugmentationZoomOutFormat.reads(json)
        }

      override def reads(json: JsValue): JsResult[AlbumAugmentationStep] = {
        json \ "augmentationType" match {
          case JsDefined(augmentationTypeValue: JsString) => readByType(augmentationTypeValue, json)
          case JsDefined(_) => JsError("Expected json string for augmentation type field")
          case jsUndefined: JsUndefined => JsError(jsUndefined.error)
        }
      }
    }

  implicit val AlbumAugmentationStepWrites: Writes[AlbumAugmentationStep] =
    new Writes[AlbumAugmentationStep] {

      override def writes(albumAugmentationStep: AlbumAugmentationStep): JsObject = {
        val childJSON =  albumAugmentationStep match {
          case photoDistort: AlbumAugmentationPhotoDistort =>
            AlbumAugmentationPhotoDistort.AlbumAugmentationPhotoDistortFormat.writes(photoDistort)
          case rotation: AlbumAugmentationRotation =>
            AlbumAugmentationRotation.AlbumAugmentationRotationFormat.writes(rotation)
          case shearing: AlbumAugmentationShearing =>
            AlbumAugmentationShearing.AlbumAugmentationShearingFormat.writes(shearing)
          case cropping: AlbumAugmentationCropping =>
            AlbumAugmentationCropping.AlbumAugmentationCroppingFormat.writes(cropping)
          case blurring: AlbumAugmentationBlurring =>
            AlbumAugmentationBlurring.AlbumAugmentationBlurringFormat.writes(blurring)
          case translation: AlbumAugmentationTranslation =>
            AlbumAugmentationTranslation.AlbumAugmentationTranslationFormat.writes(translation)
          case occlusion: AlbumAugmentationOcclusion =>
            AlbumAugmentationOcclusion.AlbumAugmentationOcclusionFormat.writes(occlusion)
          case saltPepper: AlbumAugmentationSaltPepper =>
            AlbumAugmentationSaltPepper.AlbumAugmentationSaltPepperFormat.writes(saltPepper)
          case noising: AlbumAugmentationNoising =>
            AlbumAugmentationNoising.AlbumAugmentationNoisingFormat.writes(noising)
          case mirroring: AlbumAugmentationMirroring =>
            AlbumAugmentationMirroring.AlbumAugmentationMirroringFormat.writes(mirroring)
          case zoomIn: AlbumAugmentationZoomIn =>
            AlbumAugmentationZoomIn.AlbumAugmentationZoomInFormat.writes(zoomIn)
          case zoomOut: AlbumAugmentationZoomOut =>
            AlbumAugmentationZoomOut.AlbumAugmentationZoomOutFormat.writes(zoomOut)
        }
        childJSON + ("augmentationType" -> AugmentationTypeFormat.writes(albumAugmentationStep.augmentationType))
      }
    }

}
