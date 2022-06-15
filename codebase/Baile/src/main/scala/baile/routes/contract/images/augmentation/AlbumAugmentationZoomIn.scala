package baile.routes.contract.images.augmentation

import baile.domain.images.augmentation.AugmentationType.ZoomIn
import baile.domain.images.augmentation.{ AugmentationParams, AugmentationType, ZoomInParams }
import play.api.libs.json.{ Json, OFormat }
import baile.utils.json.CommonFormats.FloatWrites

case class AlbumAugmentationZoomIn(
  ratios: Seq[Float],
  resize: Boolean,
  bloatFactor: Int
) extends AlbumAugmentationStep {
  override val augmentationType: AugmentationType = ZoomIn

  override def toDomain: AugmentationParams = ZoomInParams(
    ratios,
    resize,
    bloatFactor
  )

}

object AlbumAugmentationZoomIn {

  def fromDomain(params: ZoomInParams): AlbumAugmentationZoomIn = AlbumAugmentationZoomIn(
    params.magnifications,
    params.resize,
    params.bloatFactor
  )

  implicit val AlbumAugmentationZoomInFormat: OFormat[AlbumAugmentationZoomIn] = Json.format[AlbumAugmentationZoomIn]

}
