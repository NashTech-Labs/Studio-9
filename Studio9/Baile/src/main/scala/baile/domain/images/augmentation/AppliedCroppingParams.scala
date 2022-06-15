package baile.domain.images.augmentation

case class AppliedCroppingParams(
  cropAreaFraction: Float,
  resize: Boolean
) extends AppliedAugmentationParams {
  override val augmentationType: AugmentationType = AugmentationType.Cropping
}
