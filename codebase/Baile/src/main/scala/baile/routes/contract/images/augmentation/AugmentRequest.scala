package baile.routes.contract.images.augmentation

import play.api.libs.json.{ Json, Reads }

case class AugmentRequest(
  outputName: Option[String],
  includeOriginalPictures: Boolean,
  bloatFactor: Int,
  augmentations: Seq[AlbumAugmentationStep],
  inLibrary: Option[Boolean]
)

object AugmentRequest {
  implicit val AugmentRequestReads: Reads[AugmentRequest] = Json.reads[AugmentRequest]
}
