package baile.domain.images.augmentation

case class CroppingParams(
  cropAreaFractions: Seq[Float],
  cropsPerImage: Int,
  resize: Boolean,
  bloatFactor: Int
) extends AugmentationParams
