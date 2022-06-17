package baile.routes.contract.images.augmentation

import baile.domain.images.augmentation.{ AppliedShearingParams, AugmentationType }
import play.api.libs.json.{ Json, OWrites }

case class PictureAugmentationShearingResponse(
  angle: Float,
  resize: Boolean
) extends PictureAugmentationParamsResponse {
  override val augmentationType: AugmentationType = AugmentationType.Shearing
}

object PictureAugmentationShearingResponse {

  def fromDomain(
    shearingParams: AppliedShearingParams
  ): PictureAugmentationShearingResponse = PictureAugmentationShearingResponse(
    shearingParams.angle,
    shearingParams.resize
  )

  implicit val PictureAugmentationShearingResponseWrites: OWrites[PictureAugmentationShearingResponse] =
    Json.writes[PictureAugmentationShearingResponse]

}
