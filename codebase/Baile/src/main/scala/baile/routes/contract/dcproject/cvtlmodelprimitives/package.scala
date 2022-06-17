package baile.routes.contract.dcproject

import baile.domain.cv.model.tlprimitives.CVTLModelPrimitiveType
import baile.utils.json.EnumWritesBuilder
import play.api.libs.json.Writes

package object cvtlmodelprimitives {

  implicit val CVTLModelPrimitiveTypeWrites: Writes[CVTLModelPrimitiveType] = EnumWritesBuilder.build {
    case CVTLModelPrimitiveType.UTLP => "UTLP"
    case CVTLModelPrimitiveType.Classifier => "CLASSIFIER"
    case CVTLModelPrimitiveType.Detector => "DETECTOR"
    case CVTLModelPrimitiveType.Decoder => "DECODER"
  }

}
