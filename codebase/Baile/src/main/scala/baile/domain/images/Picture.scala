package baile.domain.images

import baile.domain.images.augmentation.AppliedAugmentation

case class Picture(
  albumId: String,
  filePath: String,
  fileName: String,
  fileSize: Option[Long],
  caption: Option[String],
  predictedCaption: Option[String],
  tags: Seq[PictureTag],
  predictedTags: Seq[PictureTag],
  meta: Map[String, String],
  originalPictureId: Option[String],
  appliedAugmentations: Option[Seq[AppliedAugmentation]]
)
