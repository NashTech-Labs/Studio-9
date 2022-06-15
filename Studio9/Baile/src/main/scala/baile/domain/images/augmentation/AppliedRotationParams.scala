package baile.domain.images.augmentation

case class AppliedRotationParams(
  angle: Float,
  resize: Boolean
) extends AppliedAugmentationParams {
  override val augmentationType: AugmentationType = AugmentationType.Rotation
}
