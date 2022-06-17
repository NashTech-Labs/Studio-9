package baile.domain.images.augmentation

sealed trait MirroringAxisToFlip

object MirroringAxisToFlip{
  case object Horizontal extends MirroringAxisToFlip
  case object Vertical extends MirroringAxisToFlip
  case object Both extends MirroringAxisToFlip
}
