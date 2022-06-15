package baile.routes.contract.images.augmentation

import baile.domain.images.augmentation.AugmentationType.ZoomOut
import baile.domain.images.augmentation.{ AugmentationParams, AugmentationType, ZoomOutParams }
import play.api.libs.json.{ Json, OFormat }
import baile.utils.json.CommonFormats.FloatWrites

case class AlbumAugmentationZoomOut(
  ratios: Seq[Float],
  resize: Boolean,
  bloatFactor: Int
) extends AlbumAugmentationStep {
  override val augmentationType: AugmentationType = ZoomOut

  override def toDomain: AugmentationParams = ZoomOutParams(
    ratios,
    resize,
    bloatFactor
  )

}

object AlbumAugmentationZoomOut {

  def fromDomain(params: ZoomOutParams): AlbumAugmentationZoomOut = AlbumAugmentationZoomOut(
    params.shrinkFactors,
    params.resize,
    params.bloatFactor
  )

  implicit val AlbumAugmentationZoomOutFormat: OFormat[AlbumAugmentationZoomOut] = Json.format[AlbumAugmentationZoomOut]

}
