package baile.routes.contract.cv

import baile.domain.cv.model.AutomatedAugmentationParams
import baile.routes.contract.images.augmentation.AlbumAugmentationStep
import play.api.libs.json.{ Json, OFormat }

case class CVAugmentationOptions(
  augmentations: Seq[AlbumAugmentationStep],
  bloatFactor: Int,
  prepareSampleAlbum: Boolean
) {

  def toDomain: AutomatedAugmentationParams = AutomatedAugmentationParams(
    augmentations = augmentations.map(_.toDomain),
    bloatFactor = bloatFactor,
    generateSampleAlbum = prepareSampleAlbum
  )

}

object CVAugmentationOptions {

  def fromDomain(in: AutomatedAugmentationParams): CVAugmentationOptions = {
    CVAugmentationOptions(
      augmentations = in.augmentations.map(AlbumAugmentationStep.fromDomain),
      bloatFactor = in.bloatFactor,
      prepareSampleAlbum = in.generateSampleAlbum
    )
  }

  implicit val AutoAugmentationParamFormat: OFormat[CVAugmentationOptions] =
    Json.format[CVAugmentationOptions]

}
