package baile.domain.images.augmentation

sealed trait AugmentationType

object AugmentationType {

  case object Rotation extends AugmentationType

  case object Shearing extends AugmentationType

  case object Noising extends AugmentationType

  case object ZoomIn extends AugmentationType

  case object ZoomOut extends AugmentationType

  case object Occlusion extends AugmentationType

  case object Translation extends AugmentationType

  case object SaltPepper extends AugmentationType

  case object Mirroring extends AugmentationType

  case object Cropping extends AugmentationType

  case object Blurring extends AugmentationType

  case object PhotoDistort extends AugmentationType

}
