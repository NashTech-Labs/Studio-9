package baile.domain.images.augmentation

case class AppliedBlurringParams(sigma: Float) extends AppliedAugmentationParams {
  override val augmentationType: AugmentationType = AugmentationType.Blurring
}
