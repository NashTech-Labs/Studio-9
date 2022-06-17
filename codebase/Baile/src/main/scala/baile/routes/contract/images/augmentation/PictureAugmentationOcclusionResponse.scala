package baile.routes.contract.images.augmentation

import baile.domain.images.augmentation.{ AppliedOcclusionParams, AugmentationType, OcclusionMode }
import play.api.libs.json.{ Json, OWrites }

case class PictureAugmentationOcclusionResponse(
  occAreaFraction: Float,
  mode: OcclusionMode,
  targetWindowSize: Int
) extends PictureAugmentationParamsResponse {
  override val augmentationType: AugmentationType = AugmentationType.Occlusion
}

object PictureAugmentationOcclusionResponse {

  def fromDomain(
    occlusionParams: AppliedOcclusionParams
  ): PictureAugmentationOcclusionResponse = PictureAugmentationOcclusionResponse(
    occlusionParams.occAreaFraction,
    occlusionParams.mode,
    occlusionParams.tarWinSize
  )

  implicit val PictureAugmentationOcclusionResponseWrites: OWrites[PictureAugmentationOcclusionResponse] =
    Json.writes[PictureAugmentationOcclusionResponse]

}
