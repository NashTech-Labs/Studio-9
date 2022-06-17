package baile.routes.contract.cv

import baile.routes.contract.common.Version
import baile.routes.contract.pipeline.operator.ParameterDefinitionResponse
import baile.services.cv.CVTLModelPrimitiveService.ExtendedCVTLModelPrimitive
import play.api.libs.json.{ Json, OWrites }
import baile.utils.CollectionExtensions.seqToOptionalNonEmptySeq

case class CVArchitectureResponse(
  id: String,
  name: String,
  moduleName: String,
  className: String,
  packageName: String,
  packageVersion: Option[Version],
  params: Option[Seq[ParameterDefinitionResponse]]
)

object CVArchitectureResponse {

  def fromDomain(
    extendedCVModelTLPrimitive: ExtendedCVTLModelPrimitive
  ): CVArchitectureResponse =
    CVArchitectureResponse(
      id = extendedCVModelTLPrimitive.id,
      name = extendedCVModelTLPrimitive.name,
      moduleName = extendedCVModelTLPrimitive.moduleName,
      className = extendedCVModelTLPrimitive.className,
      packageName = extendedCVModelTLPrimitive.packageName,
      packageVersion = extendedCVModelTLPrimitive.packageVersion.map(Version.fromDomain),
      params = seqToOptionalNonEmptySeq(extendedCVModelTLPrimitive.params.map(ParameterDefinitionResponse.fromDomain))
    )

  implicit val CVArchitectureResponseWrites: OWrites[CVArchitectureResponse] = Json.writes[CVArchitectureResponse]

}
