package baile.domain.images.augmentation

case class ZoomOutParams(
  shrinkFactors: Seq[Float],
  resize: Boolean,
  bloatFactor: Int
) extends AugmentationParams
