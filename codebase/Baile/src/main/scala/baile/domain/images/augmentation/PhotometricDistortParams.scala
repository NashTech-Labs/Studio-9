package baile.domain.images.augmentation

case class PhotometricDistortParams(
  alphaBounds: PhotometricDistortAlphaBounds,
  deltaMax: Float,
  bloatFactor: Int
) extends AugmentationParams
