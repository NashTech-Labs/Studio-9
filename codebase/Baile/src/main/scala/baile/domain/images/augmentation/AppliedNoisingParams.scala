package baile.domain.images.augmentation

case class AppliedNoisingParams(noiseSignalRatio: Float) extends AppliedAugmentationParams {
  override val augmentationType: AugmentationType = AugmentationType.Noising
}
