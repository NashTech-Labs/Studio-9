package baile.domain.images.augmentation

case class AppliedPhotometricDistortParams(
  alphaContrast: Float,
  deltaMax: Float,
  alphaSaturation: Float,
  deltaHue: Float
) extends AppliedAugmentationParams {
  override val augmentationType: AugmentationType = AugmentationType.PhotoDistort
}
