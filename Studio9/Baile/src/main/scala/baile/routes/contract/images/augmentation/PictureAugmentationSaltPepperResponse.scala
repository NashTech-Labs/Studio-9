package baile.routes.contract.images.augmentation

import baile.domain.images.augmentation.{ AppliedSaltPepperParams, AugmentationType }
import play.api.libs.json.{ Json, OWrites }

case class PictureAugmentationSaltPepperResponse(
  knockoutFraction: Float,
  pepperProbability: Double
) extends PictureAugmentationParamsResponse {
  override val augmentationType: AugmentationType = AugmentationType.SaltPepper
}

object PictureAugmentationSaltPepperResponse {

  def fromDomain(
    saltPepperParams: AppliedSaltPepperParams
  ): PictureAugmentationSaltPepperResponse = PictureAugmentationSaltPepperResponse(
    saltPepperParams.knockoutFraction,
    saltPepperParams.pepperProbability
  )

  implicit val PictureAugmentationSaltPepperResponseWrites: OWrites[PictureAugmentationSaltPepperResponse] =
    Json.writes[PictureAugmentationSaltPepperResponse]

}
