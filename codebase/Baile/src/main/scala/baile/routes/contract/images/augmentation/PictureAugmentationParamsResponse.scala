package baile.routes.contract.images.augmentation

import baile.domain.images.augmentation._
import play.api.libs.json.OWrites

trait PictureAugmentationParamsResponse {
  val augmentationType: AugmentationType
}

object PictureAugmentationParamsResponse {

  def fromDomain(
    augmentationsApplied: AppliedAugmentationParams
  ): PictureAugmentationParamsResponse = augmentationsApplied match {
    case rotationParams: AppliedRotationParams =>
      PictureAugmentationRotationResponse.fromDomain(rotationParams)
    case shearingParams: AppliedShearingParams =>
      PictureAugmentationShearingResponse.fromDomain(shearingParams)
    case noisingParams: AppliedNoisingParams =>
      PictureAugmentationNoisingResponse.fromDomain(noisingParams)
    case occlusionParams: AppliedOcclusionParams =>
      PictureAugmentationOcclusionResponse.fromDomain(occlusionParams)
    case saltPepperParams: AppliedSaltPepperParams =>
      PictureAugmentationSaltPepperResponse.fromDomain(saltPepperParams)
    case mirroringParams: AppliedMirroringParams =>
      PictureAugmentationMirroringResponse.fromDomain(mirroringParams)
    case blurringParams: AppliedBlurringParams =>
      PictureAugmentationBlurringResponse.fromDomain(blurringParams)
    case croppingParams: AppliedCroppingParams =>
      PictureAugmentationCroppingResponse.fromDomain(croppingParams)
    case translationParams: AppliedTranslationParams =>
      PictureAugmentationTranslationResponse.fromDomain(translationParams)
    case zoomInParams: AppliedZoomInParams =>
      PictureAugmentationZoomInResponse.fromDomain(zoomInParams)
    case zoomOutParams: AppliedZoomOutParams =>
      PictureAugmentationZoomOutResponse.fromDomain(zoomOutParams)
    case photoDistortParams: AppliedPhotometricDistortParams =>
      PictureAugmentationPhotoDistortResponse.fromDomain(photoDistortParams)
  }

  implicit val PictureAugmentationParamsResponseWrites: OWrites[PictureAugmentationParamsResponse] =
    OWrites[PictureAugmentationParamsResponse] { response =>
      val childJSON = response match {
        case data: PictureAugmentationPhotoDistortResponse =>
          PictureAugmentationPhotoDistortResponse.PictureAugmentationPhotoDistortResponseWrites.writes(data)
        case data: PictureAugmentationBlurringResponse =>
          PictureAugmentationBlurringResponse.PictureAugmentationBlurringResponseWrites.writes(data)
        case data: PictureAugmentationCroppingResponse =>
          PictureAugmentationCroppingResponse.PictureAugmentationCroppingResponseWrites.writes(data)
        case data: PictureAugmentationMirroringResponse =>
          PictureAugmentationMirroringResponse.PictureAugmentationMirroringResponseWrites.writes(data)
        case data: PictureAugmentationNoisingResponse =>
          PictureAugmentationNoisingResponse.PictureAugmentationNoisingResponseWrites.writes(data)
        case data: PictureAugmentationOcclusionResponse =>
          PictureAugmentationOcclusionResponse.PictureAugmentationOcclusionResponseWrites.writes(data)
        case data: PictureAugmentationRotationResponse =>
          PictureAugmentationRotationResponse.PictureAugmentationRotationResponseWrites.writes(data)
        case data: PictureAugmentationSaltPepperResponse =>
          PictureAugmentationSaltPepperResponse.PictureAugmentationSaltPepperResponseWrites.writes(data)
        case data: PictureAugmentationShearingResponse =>
          PictureAugmentationShearingResponse.PictureAugmentationShearingResponseWrites.writes(data)
        case data: PictureAugmentationTranslationResponse =>
          PictureAugmentationTranslationResponse.PictureAugmentationTranslationResponseWrites.writes(data)
        case data: PictureAugmentationZoomInResponse =>
          PictureAugmentationZoomInResponse.PictureAugmentationZoomInResponseWrites.writes(data)
        case data: PictureAugmentationZoomOutResponse =>
          PictureAugmentationZoomOutResponse.PictureAugmentationZoomOutResponseWrites.writes(data)
        case _ => throw new IllegalArgumentException(
          s"AppliedAugmentationParamsResponse serializer can't serialize ${ response.getClass }"
        )
      }
      childJSON + ("augmentationType" -> AugmentationTypeFormat.writes(response.augmentationType))
    }

}
