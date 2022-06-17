package baile.domain.images.augmentation

sealed trait OcclusionMode

object OcclusionMode {
  case object Background extends OcclusionMode
  case object Zero extends OcclusionMode
}
