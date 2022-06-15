package baile.routes.contract.images.augmentation

import baile.domain.images.augmentation.{ AppliedRotationParams, AugmentationType }
import play.api.libs.json.{ Json, OWrites }

case class PictureAugmentationRotationResponse(
  angle: Float,
  resize: Boolean
) extends PictureAugmentationParamsResponse {
  override val augmentationType: AugmentationType = AugmentationType.Rotation
}

object PictureAugmentationRotationResponse {

  def fromDomain(
    rotationParams: AppliedRotationParams
  ): PictureAugmentationRotationResponse = PictureAugmentationRotationResponse(
    rotationParams.angle,
    rotationParams.resize
  )

  implicit val PictureAugmentationRotationResponseWrites: OWrites[PictureAugmentationRotationResponse] =
    Json.writes[PictureAugmentationRotationResponse]

}
