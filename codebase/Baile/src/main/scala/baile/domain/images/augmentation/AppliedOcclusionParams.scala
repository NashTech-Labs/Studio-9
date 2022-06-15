package baile.domain.images.augmentation

case class AppliedOcclusionParams(
  occAreaFraction: Float,
  mode: OcclusionMode,
  isSarAlbum: Boolean,
  tarWinSize: Int
) extends AppliedAugmentationParams {
  override val augmentationType: AugmentationType = AugmentationType.Occlusion
}
