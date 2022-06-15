package baile.services.cv.model.util.export.format.v1

private[v1] sealed trait CVModelLocalizationMode

private[v1] object CVModelLocalizationMode {
  case object Tags extends CVModelLocalizationMode
  case object Captions extends CVModelLocalizationMode
}

