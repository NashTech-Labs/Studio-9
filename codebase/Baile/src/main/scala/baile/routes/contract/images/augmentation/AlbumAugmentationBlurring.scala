package baile.routes.contract.images.augmentation

import baile.domain.images.augmentation.AugmentationType.Blurring
import baile.domain.images.augmentation.{ AugmentationParams, AugmentationType, BlurringParams }
import play.api.libs.json.{ Json, OFormat }
import baile.utils.json.CommonFormats.FloatWrites

case class AlbumAugmentationBlurring(sigmas: Seq[Float], bloatFactor: Int) extends AlbumAugmentationStep {
  override val augmentationType: AugmentationType = Blurring

  override def toDomain: AugmentationParams = BlurringParams(sigmas, bloatFactor)
}

object AlbumAugmentationBlurring {

  def fromDomain(params: BlurringParams): AlbumAugmentationBlurring = AlbumAugmentationBlurring(
    params.sigmaList,
    params.bloatFactor
  )

  implicit val AlbumAugmentationBlurringFormat: OFormat[AlbumAugmentationBlurring] =
    Json.format[AlbumAugmentationBlurring]

}
