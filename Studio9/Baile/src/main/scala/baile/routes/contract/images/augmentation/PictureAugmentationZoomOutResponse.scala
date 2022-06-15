package baile.routes.contract.images.augmentation

import baile.domain.images.augmentation.{ AppliedZoomOutParams, AugmentationType }
import play.api.libs.json.{ Json, OWrites }

case class PictureAugmentationZoomOutResponse(
  ratio: Float,
  resize: Boolean
) extends PictureAugmentationParamsResponse {
  override val augmentationType: AugmentationType = AugmentationType.ZoomOut
}

object PictureAugmentationZoomOutResponse {

  def fromDomain(
    zoomOutParams: AppliedZoomOutParams
  ): PictureAugmentationZoomOutResponse = PictureAugmentationZoomOutResponse(
    zoomOutParams.shrinkFactor,
    zoomOutParams.resize
  )

  implicit val PictureAugmentationZoomOutResponseWrites: OWrites[PictureAugmentationZoomOutResponse] =
    Json.writes[PictureAugmentationZoomOutResponse]

}
