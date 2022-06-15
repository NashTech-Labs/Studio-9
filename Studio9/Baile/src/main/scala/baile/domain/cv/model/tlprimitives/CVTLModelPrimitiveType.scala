package baile.domain.cv.model.tlprimitives

sealed trait CVTLModelPrimitiveType

object CVTLModelPrimitiveType {

  case object UTLP extends CVTLModelPrimitiveType

  case object Decoder extends CVTLModelPrimitiveType

  case object Classifier extends CVTLModelPrimitiveType

  case object Detector extends CVTLModelPrimitiveType

}
