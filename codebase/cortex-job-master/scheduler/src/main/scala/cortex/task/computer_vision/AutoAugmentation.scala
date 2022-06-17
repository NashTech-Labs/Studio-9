package cortex.task.computer_vision

import cortex.task.data_augmentation.DataAugmentationParams.{
  AugmentationParams,
  AugmentationType,
  TransformImagesResult,
  AugmentationTypeReads
}
import cortex.JsonSupport.SnakeJson
import play.api.libs.json._

object AutoAugmentation {

  case class AutoAugmentationParams(
      augmentations:           Seq[AugmentationParams],
      bloatFactor:             Int,
      generateSampleAlbum:     Boolean,
      sampleAlbumTargetPrefix: String
  )

  case class AutoAugmentationResult(
      // sample album augmented images
      transformResult: TransformImagesResult,
      // applied augmentations to the whole album
      // todo: there might be many augmentations with the same name in one request
      augmentationSummary: Map[AugmentationType, Long]
  )

  implicit val autoAugmentationParamsWrites: OWrites[AutoAugmentationParams] = SnakeJson.writes[AutoAugmentationParams]

  private implicit val mapReads: Reads[Map[AugmentationType, Long]] = Reads.mapReads(
    (value: String) => AugmentationTypeReads.reads(JsString(value))
  )

  implicit val transformImagesResultReads: Reads[TransformImagesResult] = SnakeJson.reads[TransformImagesResult]
  implicit val autoAugmentationResultReads: Reads[AutoAugmentationResult] = SnakeJson.reads[AutoAugmentationResult]
}
