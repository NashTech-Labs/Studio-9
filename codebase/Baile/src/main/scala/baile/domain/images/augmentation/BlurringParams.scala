package baile.domain.images.augmentation

case class BlurringParams(sigmaList: Seq[Float], bloatFactor: Int) extends AugmentationParams
