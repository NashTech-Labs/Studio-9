package baile.domain.images.augmentation

case class AppliedShearingParams(
  angle: Float,
  resize: Boolean
) extends AppliedAugmentationParams {
  override val augmentationType: AugmentationType = AugmentationType.Shearing
}
