package baile.domain.images

sealed trait AlbumLabelMode

object AlbumLabelMode {
  case object Classification extends AlbumLabelMode
  case object Localization extends AlbumLabelMode
}
