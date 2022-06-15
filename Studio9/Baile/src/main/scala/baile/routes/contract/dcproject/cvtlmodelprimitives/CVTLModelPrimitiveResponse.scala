package baile.routes.contract.dcproject.cvtlmodelprimitives

import baile.domain.cv.model.tlprimitives.{ CVTLModelPrimitive, CVTLModelPrimitiveType }
import baile.routes.contract.pipeline.operator.ParameterDefinitionResponse
import play.api.libs.json.{ Json, OWrites }

case class CVTLModelPrimitiveResponse(
  name: String,
  description: Option[String],
  moduleName: String,
  className: String,
  operatorType: CVTLModelPrimitiveType,
  params: Seq[ParameterDefinitionResponse]
)

object CVTLModelPrimitiveResponse {

  implicit val CVTLModelPrimitiveResponseWrites: OWrites[CVTLModelPrimitiveResponse] =
    Json.writes[CVTLModelPrimitiveResponse]

  def fromDomain(cvTlModelPrimitive: CVTLModelPrimitive): CVTLModelPrimitiveResponse = {
    CVTLModelPrimitiveResponse(
      name = cvTlModelPrimitive.name,
      description = cvTlModelPrimitive.description,
      moduleName = cvTlModelPrimitive.moduleName,
      className = cvTlModelPrimitive.className,
      operatorType = cvTlModelPrimitive.cvTLModelPrimitiveType,
      params = cvTlModelPrimitive.params.map(ParameterDefinitionResponse.fromDomain)
    )
  }

}
