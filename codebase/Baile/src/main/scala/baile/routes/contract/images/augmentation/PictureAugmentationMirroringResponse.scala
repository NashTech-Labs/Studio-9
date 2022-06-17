package baile.routes.contract.images.augmentation

import baile.domain.images.augmentation.{ AppliedMirroringParams, AugmentationType, MirroringAxisToFlip }
import play.api.libs.json.{ Json, OWrites }

case class PictureAugmentationMirroringResponse(
  flipAxis: Int
) extends PictureAugmentationParamsResponse {
  override val augmentationType: AugmentationType = AugmentationType.Mirroring
}

object PictureAugmentationMirroringResponse {

  def fromDomain(
    mirroringParams: AppliedMirroringParams
  ): PictureAugmentationMirroringResponse = PictureAugmentationMirroringResponse(
    mirroringParams.axisFlipped match {
      case MirroringAxisToFlip.Horizontal => 0
      case MirroringAxisToFlip.Vertical => 1
      case MirroringAxisToFlip.Both => 2
    }
  )

  implicit val PictureAugmentationMirroringResponseWrites: OWrites[PictureAugmentationMirroringResponse] =
    Json.writes[PictureAugmentationMirroringResponse]

}
