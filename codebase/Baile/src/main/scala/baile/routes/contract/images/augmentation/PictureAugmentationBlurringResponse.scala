package baile.routes.contract.images.augmentation

import baile.domain.images.augmentation.{ AppliedBlurringParams, AugmentationType }
import play.api.libs.json.{ Json, OWrites }

case class PictureAugmentationBlurringResponse(sigma: Float) extends PictureAugmentationParamsResponse {
  override val augmentationType: AugmentationType = AugmentationType.Blurring
}

object PictureAugmentationBlurringResponse {

  def fromDomain(
    blurringParams: AppliedBlurringParams
  ): PictureAugmentationBlurringResponse = PictureAugmentationBlurringResponse(
    blurringParams.sigma
  )

  implicit val PictureAugmentationBlurringResponseWrites: OWrites[PictureAugmentationBlurringResponse] =
    Json.writes[PictureAugmentationBlurringResponse]

}
