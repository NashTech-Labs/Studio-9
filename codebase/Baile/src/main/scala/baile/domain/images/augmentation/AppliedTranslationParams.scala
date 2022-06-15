package baile.domain.images.augmentation

case class AppliedTranslationParams(
  translateFraction: Float,
  mode: TranslationMode,
  resize: Boolean
) extends AppliedAugmentationParams {
  override val augmentationType: AugmentationType = AugmentationType.Translation
}
