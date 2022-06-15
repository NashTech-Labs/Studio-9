package baile.routes.contract.images.augmentation

import baile.domain.images.augmentation.AugmentationType.Noising
import baile.domain.images.augmentation.{ AugmentationParams, AugmentationType, NoisingParams }
import play.api.libs.json.{ Json, OFormat }
import baile.utils.json.CommonFormats.FloatWrites

case class AlbumAugmentationNoising(
  noiseSignalRatios: Seq[Float],
  bloatFactor: Int
) extends AlbumAugmentationStep {
  override val augmentationType: AugmentationType = Noising

  override def toDomain: AugmentationParams = NoisingParams(
    noiseSignalRatios,
    bloatFactor
  )

}

object AlbumAugmentationNoising {

  def fromDomain(params: NoisingParams): AlbumAugmentationNoising = AlbumAugmentationNoising(
    params.noiseSignalRatios,
    params.bloatFactor
  )

  implicit val AlbumAugmentationNoisingFormat: OFormat[AlbumAugmentationNoising] = Json.format[AlbumAugmentationNoising]

}
