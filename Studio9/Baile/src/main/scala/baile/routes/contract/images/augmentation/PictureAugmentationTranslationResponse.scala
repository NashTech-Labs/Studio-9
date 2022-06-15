package baile.routes.contract.images.augmentation

import baile.domain.images.augmentation.{ AppliedTranslationParams, AugmentationType, TranslationMode }
import play.api.libs.json.{ Json, OWrites }

case class PictureAugmentationTranslationResponse(
  translateFraction: Float,
  mode: TranslationMode,
  resize: Boolean
) extends PictureAugmentationParamsResponse {
  override val augmentationType: AugmentationType = AugmentationType.Translation
}

object PictureAugmentationTranslationResponse{

  def fromDomain(
    translationParams: AppliedTranslationParams
  ): PictureAugmentationTranslationResponse = PictureAugmentationTranslationResponse(
    translationParams.translateFraction,
    translationParams.mode,
    translationParams.resize
  )

  implicit val PictureAugmentationTranslationResponseWrites: OWrites[PictureAugmentationTranslationResponse] =
    Json.writes[PictureAugmentationTranslationResponse]

}
