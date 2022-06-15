package baile.domain.images.augmentation

case class AppliedZoomOutParams(
  shrinkFactor: Float,
  resize: Boolean
) extends AppliedAugmentationParams {
  override val augmentationType: AugmentationType = AugmentationType.ZoomOut
}
