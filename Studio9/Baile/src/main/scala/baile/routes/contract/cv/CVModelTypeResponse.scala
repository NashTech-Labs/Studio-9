package baile.routes.contract.cv

import baile.routes.contract.common.Version
import baile.routes.contract.pipeline.operator.ParameterDefinitionResponse
import baile.services.cv.CVTLModelPrimitiveService.ExtendedCVTLModelPrimitive
import play.api.libs.json.{ Json, OWrites }
import baile.utils.CollectionExtensions.seqToOptionalNonEmptySeq

case class CVModelTypeResponse(
  id: String,
  name: String,
  moduleName: String,
  className: String,
  packageName: String,
  packageVersion: Option[Version],
  isNeural: Boolean,
  params: Option[Seq[ParameterDefinitionResponse]]
)

object CVModelTypeResponse {

  def fromDomain(
    extendedCVModelTLPrimitive: ExtendedCVTLModelPrimitive
  ): CVModelTypeResponse =
    CVModelTypeResponse(
      id = extendedCVModelTLPrimitive.id,
      name = extendedCVModelTLPrimitive.name,
      moduleName = extendedCVModelTLPrimitive.moduleName,
      className = extendedCVModelTLPrimitive.className,
      packageName = extendedCVModelTLPrimitive.packageName,
      packageVersion = extendedCVModelTLPrimitive.packageVersion.map(Version.fromDomain),
      isNeural = extendedCVModelTLPrimitive.isNeural,
      params = seqToOptionalNonEmptySeq(extendedCVModelTLPrimitive.params.map(ParameterDefinitionResponse.fromDomain))
    )

  implicit val CVModelTypeResponseWrites: OWrites[CVModelTypeResponse] = Json.writes[CVModelTypeResponse]

}
