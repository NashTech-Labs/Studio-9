package baile.domain.images.augmentation

case class ZoomInParams(
  magnifications: Seq[Float],
  resize: Boolean,
  bloatFactor: Int
) extends AugmentationParams
