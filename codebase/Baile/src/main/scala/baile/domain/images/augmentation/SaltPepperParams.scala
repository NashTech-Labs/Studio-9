package baile.domain.images.augmentation

case class SaltPepperParams(
  knockoutFractions: Seq[Float],
  pepperProbability: Float,
  bloatFactor: Int
) extends AugmentationParams
