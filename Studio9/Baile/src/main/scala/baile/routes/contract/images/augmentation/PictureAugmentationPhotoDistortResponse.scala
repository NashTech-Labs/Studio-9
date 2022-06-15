package baile.routes.contract.images.augmentation

import baile.domain.images.augmentation.{ AppliedPhotometricDistortParams, AugmentationType }
import play.api.libs.json.{ Json, OWrites }

case class PictureAugmentationPhotoDistortResponse(
  alphaContrast: Float,
  alphaSaturation: Float,
  deltaHue: Float
) extends PictureAugmentationParamsResponse {
  override val augmentationType: AugmentationType = AugmentationType.PhotoDistort
}

object PictureAugmentationPhotoDistortResponse {

  def fromDomain(
    photoDistortParams: AppliedPhotometricDistortParams
  ): PictureAugmentationPhotoDistortResponse = PictureAugmentationPhotoDistortResponse(
    alphaContrast = photoDistortParams.alphaContrast,
    alphaSaturation = photoDistortParams.alphaSaturation,
    deltaHue = photoDistortParams.deltaHue
  )

  implicit val PictureAugmentationPhotoDistortResponseWrites: OWrites[PictureAugmentationPhotoDistortResponse] =
    Json.writes[PictureAugmentationPhotoDistortResponse]

}
