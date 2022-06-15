package baile.routes.contract.images.augmentation

import baile.domain.images.augmentation.AugmentationType.SaltPepper
import baile.domain.images.augmentation.{ AugmentationParams, AugmentationType, SaltPepperParams }
import play.api.libs.json.{ Json, OFormat }
import baile.utils.json.CommonFormats.FloatWrites

case class AlbumAugmentationSaltPepper(
  knockoutFractions: Seq[Float],
  pepperProbability: Float,
  bloatFactor: Int
) extends AlbumAugmentationStep {
  override val augmentationType: AugmentationType = SaltPepper

  override def toDomain: AugmentationParams = SaltPepperParams(
    knockoutFractions,
    pepperProbability,
    bloatFactor
  )

}

object AlbumAugmentationSaltPepper {

  def fromDomain(params: SaltPepperParams): AlbumAugmentationSaltPepper = AlbumAugmentationSaltPepper(
    params.knockoutFractions,
    params.pepperProbability,
    params.bloatFactor
  )

  implicit val AlbumAugmentationSaltPepperFormat: OFormat[AlbumAugmentationSaltPepper] =
    Json.format[AlbumAugmentationSaltPepper]

}
