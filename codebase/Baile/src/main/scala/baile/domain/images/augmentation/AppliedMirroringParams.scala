package baile.domain.images.augmentation

case class AppliedMirroringParams(axisFlipped: MirroringAxisToFlip) extends AppliedAugmentationParams {
  override val augmentationType: AugmentationType = AugmentationType.Mirroring
}
