package baile.routes.contract.images.augmentation

import baile.domain.images.augmentation.AugmentationType.Shearing
import baile.domain.images.augmentation.{ AugmentationParams, AugmentationType, ShearingParams }
import play.api.libs.json.{ Json, OFormat }
import baile.utils.json.CommonFormats.FloatWrites

case class AlbumAugmentationShearing(
  angles: Seq[Float],
  resize: Boolean,
  bloatFactor: Int
) extends AlbumAugmentationStep {
  override val augmentationType: AugmentationType = Shearing

  override def toDomain: AugmentationParams = ShearingParams(
    angles,
    resize,
    bloatFactor
  )
}

object AlbumAugmentationShearing {

  def fromDomain(params: ShearingParams): AlbumAugmentationShearing = AlbumAugmentationShearing(
    params.angles,
    params.resize,
    params.bloatFactor
  )

  implicit val AlbumAugmentationShearingFormat: OFormat[AlbumAugmentationShearing] =
    Json.format[AlbumAugmentationShearing]

}
