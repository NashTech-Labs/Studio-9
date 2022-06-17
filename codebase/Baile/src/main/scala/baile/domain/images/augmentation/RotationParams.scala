package baile.domain.images.augmentation

case class RotationParams(
  angles: Seq[Float],
  resize: Boolean,
  bloatFactor: Int
) extends AugmentationParams
