package baile.domain.images.augmentation

sealed trait TranslationMode

object TranslationMode {
  case object Reflect extends TranslationMode
  case object Constant extends TranslationMode
}
