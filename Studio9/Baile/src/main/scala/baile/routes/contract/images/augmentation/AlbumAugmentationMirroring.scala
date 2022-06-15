package baile.routes.contract.images.augmentation

import baile.domain.images.augmentation.AugmentationType.Mirroring
import baile.domain.images.augmentation._
import play.api.libs.json.{ Json, OFormat }

case class AlbumAugmentationMirroring(flipAxes: Seq[Int],
  bloatFactor: Int
) extends AlbumAugmentationStep {
  override val augmentationType: AugmentationType = Mirroring

  override def toDomain: AugmentationParams = MirroringParams(
    flipAxes.map {
      case 0 => MirroringAxisToFlip.Horizontal
      case 1 => MirroringAxisToFlip.Vertical
      case 2 => MirroringAxisToFlip.Both
    },
    bloatFactor
  )
}

object AlbumAugmentationMirroring {

  def fromDomain(params: MirroringParams): AlbumAugmentationMirroring = AlbumAugmentationMirroring(
    params.axesToFlip map {
      case MirroringAxisToFlip.Horizontal => 0
      case MirroringAxisToFlip.Vertical => 1
      case MirroringAxisToFlip.Both => 2
    },
    params.bloatFactor
  )

  implicit val AlbumAugmentationMirroringFormat: OFormat[AlbumAugmentationMirroring] =
    Json.format[AlbumAugmentationMirroring]

}
