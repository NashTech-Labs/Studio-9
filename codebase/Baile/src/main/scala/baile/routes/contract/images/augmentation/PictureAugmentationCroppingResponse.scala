package baile.routes.contract.images.augmentation

import baile.domain.images.augmentation.{ AppliedCroppingParams, AugmentationType }
import play.api.libs.json.{ Json, OWrites }

case class PictureAugmentationCroppingResponse(
  cropAreaFraction: Float,
  resize: Boolean
) extends PictureAugmentationParamsResponse {
  override val augmentationType: AugmentationType = AugmentationType.Cropping
}

object PictureAugmentationCroppingResponse {

  def fromDomain(
    croppingParams: AppliedCroppingParams
  ): PictureAugmentationCroppingResponse = PictureAugmentationCroppingResponse(
    croppingParams.cropAreaFraction,
    croppingParams.resize
  )

  implicit val PictureAugmentationCroppingResponseWrites: OWrites[PictureAugmentationCroppingResponse] =
    Json.writes[PictureAugmentationCroppingResponse]

}
