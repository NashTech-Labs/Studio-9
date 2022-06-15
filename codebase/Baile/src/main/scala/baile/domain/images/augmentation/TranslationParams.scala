package baile.domain.images.augmentation

case class TranslationParams(
  translateFractions: Seq[Float],
  mode: TranslationMode,
  resize: Boolean,
  bloatFactor: Int
) extends AugmentationParams
