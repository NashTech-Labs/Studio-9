package baile.domain.images.augmentation

case class ShearingParams(
  angles: Seq[Float],
  resize: Boolean,
  bloatFactor: Int
) extends AugmentationParams
