package baile.routes.contract.images.augmentation

import baile.domain.images.augmentation.{ AppliedNoisingParams, AugmentationType }
import play.api.libs.json.{ Json, OWrites }

case class PictureAugmentationNoisingResponse(
  noiseSignalRatio: Float
) extends PictureAugmentationParamsResponse {
  override val augmentationType: AugmentationType = AugmentationType.Noising
}

object PictureAugmentationNoisingResponse {

  def fromDomain(
    noisingParams: AppliedNoisingParams
  ): PictureAugmentationNoisingResponse = PictureAugmentationNoisingResponse(
    noisingParams.noiseSignalRatio
  )

  implicit val PictureAugmentationNoisingResponseWrites: OWrites[PictureAugmentationNoisingResponse] =
    Json.writes[PictureAugmentationNoisingResponse]

}
