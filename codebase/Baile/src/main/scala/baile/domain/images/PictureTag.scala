package baile.domain.images

case class PictureTag(
  label: String,
  area: Option[PictureTagArea] = None,
  confidence: Option[Double] = None
)
