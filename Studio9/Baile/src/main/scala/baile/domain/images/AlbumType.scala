package baile.domain.images

sealed trait AlbumType

object AlbumType {

  case object Source extends AlbumType

  case object Derived extends AlbumType

  case object TrainResults extends AlbumType

}
