package baile.routes.contract.images.augmentation

import baile.domain.images.augmentation.{ AppliedZoomInParams, AugmentationType }
import play.api.libs.json.{ Json, OWrites }

case class PictureAugmentationZoomInResponse(
  ratio: Float,
  resize: Boolean
) extends PictureAugmentationParamsResponse {
  override val augmentationType: AugmentationType = AugmentationType.ZoomIn
}

object PictureAugmentationZoomInResponse {

  def fromDomain(
    zoomInParams: AppliedZoomInParams
  ): PictureAugmentationZoomInResponse = PictureAugmentationZoomInResponse(
    zoomInParams.magnification,
    zoomInParams.resize
  )

  implicit val PictureAugmentationZoomInResponseWrites: OWrites[PictureAugmentationZoomInResponse] =
    Json.writes[PictureAugmentationZoomInResponse]

}
