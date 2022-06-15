package baile.routes.contract.images.augmentation

import baile.domain.images.augmentation.AugmentationType.Occlusion
import baile.domain.images.augmentation._
import play.api.libs.json.{ Json, OFormat }
import baile.utils.json.CommonFormats.FloatWrites

case class AlbumAugmentationOcclusion(
  occAreaFractions: Seq[Float],
  mode: OcclusionMode,
  isSARAlbum: Boolean,
  targetWindowSize: Int,
  bloatFactor: Int
) extends AlbumAugmentationStep {
  override val augmentationType: AugmentationType = Occlusion

  override def toDomain: AugmentationParams = OcclusionParams(
    occAreaFractions,
    mode,
    isSARAlbum,
    targetWindowSize,
    bloatFactor
  )

}

object AlbumAugmentationOcclusion {

  def fromDomain(params: OcclusionParams): AlbumAugmentationOcclusion = AlbumAugmentationOcclusion(
    params.occAreaFractions,
    params.mode,
    params.isSarAlbum,
    params.tarWinSize,
    params.bloatFactor
  )

  implicit val AlbumAugmentationOcclusionFormat: OFormat[AlbumAugmentationOcclusion] =
    Json.format[AlbumAugmentationOcclusion]

}
