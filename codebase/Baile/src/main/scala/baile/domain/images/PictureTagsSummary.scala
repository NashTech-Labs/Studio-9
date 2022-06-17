package baile.domain.images

case class PictureTagsSummary(
  fileName: String,
  tags: Seq[PictureTag],
  predictedTags: Seq[PictureTag]
)
