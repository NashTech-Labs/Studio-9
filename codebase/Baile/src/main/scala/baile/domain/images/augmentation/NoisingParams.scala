package baile.domain.images.augmentation

case class NoisingParams(noiseSignalRatios: Seq[Float], bloatFactor: Int) extends AugmentationParams
