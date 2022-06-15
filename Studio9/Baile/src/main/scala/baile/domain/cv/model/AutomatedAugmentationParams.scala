package baile.domain.cv.model

import baile.domain.images.augmentation.AugmentationParams

case class AutomatedAugmentationParams(
  augmentations: Seq[AugmentationParams],
  bloatFactor: Int,
  generateSampleAlbum: Boolean
)
