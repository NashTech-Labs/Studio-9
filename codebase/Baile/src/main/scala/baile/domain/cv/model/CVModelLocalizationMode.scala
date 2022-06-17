package baile.domain.cv.model

sealed trait CVModelLocalizationMode

object CVModelLocalizationMode {
  case object Tags extends CVModelLocalizationMode
  case object Captions extends CVModelLocalizationMode
}
