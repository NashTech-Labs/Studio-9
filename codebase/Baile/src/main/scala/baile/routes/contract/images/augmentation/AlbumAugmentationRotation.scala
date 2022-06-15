package baile.routes.contract.images.augmentation

import baile.domain.images.augmentation.AugmentationType.Rotation
import baile.domain.images.augmentation.{ AugmentationParams, AugmentationType, RotationParams }
import play.api.libs.json.{ Json, OFormat }
import baile.utils.json.CommonFormats.FloatWrites

case class AlbumAugmentationRotation(
  angles: Seq[Float],
  resize: Boolean,
  bloatFactor: Int
) extends AlbumAugmentationStep {
  override val augmentationType: AugmentationType = Rotation

  override def toDomain: AugmentationParams = RotationParams(
    angles,
    resize,
    bloatFactor
  )
}

object AlbumAugmentationRotation {

  def fromDomain(params: RotationParams): AlbumAugmentationRotation = AlbumAugmentationRotation(
    params.angles,
    params.resize,
    params.bloatFactor
  )

  implicit val AlbumAugmentationRotationFormat: OFormat[AlbumAugmentationRotation] =
    Json.format[AlbumAugmentationRotation]

}
