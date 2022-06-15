package baile.services.cv.model.util.export.format.v1

private[v1] sealed trait AlbumLabelMode

private[v1] object AlbumLabelMode {
  case object Classification extends AlbumLabelMode
  case object Localization extends AlbumLabelMode
}
