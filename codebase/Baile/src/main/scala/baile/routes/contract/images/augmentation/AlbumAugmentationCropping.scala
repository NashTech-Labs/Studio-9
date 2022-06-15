package baile.routes.contract.images.augmentation

import baile.domain.images.augmentation.AugmentationType.Cropping
import baile.domain.images.augmentation.{ AugmentationParams, AugmentationType, CroppingParams }
import play.api.libs.json.{ Json, OFormat }
import baile.utils.json.CommonFormats.FloatWrites

case class AlbumAugmentationCropping(
  cropAreaFractions: Seq[Float],
  cropsPerImage: Int,
  resize: Boolean,
  bloatFactor: Int
) extends AlbumAugmentationStep {
  override val augmentationType: AugmentationType = Cropping

  override def toDomain: AugmentationParams = CroppingParams(cropAreaFractions, cropsPerImage, resize, bloatFactor)

}

object AlbumAugmentationCropping {

  def fromDomain(params: CroppingParams): AlbumAugmentationCropping = AlbumAugmentationCropping(
    params.cropAreaFractions,
    params.cropsPerImage,
    params.resize,
    params.bloatFactor
  )

  implicit val AlbumAugmentationCroppingFormat: OFormat[AlbumAugmentationCropping] =
    Json.format[AlbumAugmentationCropping]

}
