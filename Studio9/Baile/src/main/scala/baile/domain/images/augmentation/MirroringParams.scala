package baile.domain.images.augmentation

case class MirroringParams(
  axesToFlip: Seq[MirroringAxisToFlip],
  bloatFactor: Int
) extends AugmentationParams
