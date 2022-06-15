package baile.routes.contract.images.augmentation

import baile.domain.images.augmentation._
import baile.domain.images.augmentation.AugmentationType.PhotoDistort
import play.api.libs.json.{ Json, OFormat }
import baile.utils.json.CommonFormats.FloatWrites

case class AlbumAugmentationPhotoDistort(
  alphaMin: Float,
  alphaMax: Float,
  deltaMax: Float,
  bloatFactor: Int
) extends AlbumAugmentationStep {
  override val augmentationType: AugmentationType = PhotoDistort

  override def toDomain: AugmentationParams = PhotometricDistortParams(
    PhotometricDistortAlphaBounds(alphaMin, alphaMax),
    deltaMax,
    bloatFactor
  )
}

object AlbumAugmentationPhotoDistort {

  def fromDomain(params: PhotometricDistortParams): AlbumAugmentationPhotoDistort = AlbumAugmentationPhotoDistort(
    params.alphaBounds.min,
    params.alphaBounds.max,
    params.deltaMax,
    params.bloatFactor
  )

  implicit val AlbumAugmentationPhotoDistortFormat: OFormat[AlbumAugmentationPhotoDistort] =
    Json.format[AlbumAugmentationPhotoDistort]

}
