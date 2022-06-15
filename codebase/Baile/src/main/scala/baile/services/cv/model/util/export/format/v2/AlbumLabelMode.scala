package baile.services.cv.model.util.export.format.v2

private[export] sealed trait AlbumLabelMode

private[export] object AlbumLabelMode {

  case object Classification extends AlbumLabelMode

  case object Localization extends AlbumLabelMode

}
