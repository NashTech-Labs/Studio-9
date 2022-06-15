package baile.domain.images.augmentation

case class AppliedZoomInParams(
  magnification: Float,
  resize: Boolean
) extends AppliedAugmentationParams {
  override val augmentationType: AugmentationType = AugmentationType.ZoomIn
}
