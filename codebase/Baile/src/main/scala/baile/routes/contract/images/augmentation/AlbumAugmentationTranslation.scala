package baile.routes.contract.images.augmentation

import baile.domain.images.augmentation.AugmentationType.Translation
import baile.domain.images.augmentation._
import play.api.libs.json.{ Json, OFormat }
import baile.utils.json.CommonFormats.FloatWrites

case class AlbumAugmentationTranslation(
  translateFractions: Seq[Float],
  mode: TranslationMode,
  resize: Boolean,
  bloatFactor: Int
) extends AlbumAugmentationStep {
  override val augmentationType: AugmentationType = Translation

  override def toDomain: AugmentationParams = TranslationParams(
    translateFractions,
    mode,
    resize,
    bloatFactor
  )

}

object AlbumAugmentationTranslation {

  def fromDomain(params: TranslationParams): AlbumAugmentationTranslation = AlbumAugmentationTranslation(
    params.translateFractions,
    params.mode,
    params.resize,
    params.bloatFactor
  )

  implicit val AlbumAugmentationTranslationFormat: OFormat[AlbumAugmentationTranslation] =
    Json.format[AlbumAugmentationTranslation]

}
