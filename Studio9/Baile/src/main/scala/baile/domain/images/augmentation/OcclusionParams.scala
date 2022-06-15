package baile.domain.images.augmentation

case class OcclusionParams(
  occAreaFractions: Seq[Float],
  mode: OcclusionMode,
  isSarAlbum: Boolean,
  tarWinSize: Int,
  bloatFactor: Int
) extends AugmentationParams
