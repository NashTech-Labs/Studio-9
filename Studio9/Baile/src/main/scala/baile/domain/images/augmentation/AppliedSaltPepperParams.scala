package baile.domain.images.augmentation

case class AppliedSaltPepperParams(
  knockoutFraction: Float,
  pepperProbability: Float
) extends AppliedAugmentationParams {
  override val augmentationType: AugmentationType = AugmentationType.SaltPepper
}
