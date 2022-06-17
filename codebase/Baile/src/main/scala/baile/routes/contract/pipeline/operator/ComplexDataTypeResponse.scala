package baile.routes.contract.pipeline.operator

import baile.domain.pipeline.ComplexDataType
import play.api.libs.json.{ Json, OWrites }

case class ComplexDataTypeResponse(
  definition: String,
  parents: Seq[ComplexDataTypeResponse],
  typeArguments: Seq[PipelineDataTypeResponse]
) extends PipelineDataTypeResponse

object ComplexDataTypeResponse {
  implicit val ComplexDataTypeResponseWrites: OWrites[ComplexDataTypeResponse] = Json.writes[ComplexDataTypeResponse]

  def fromDomain(complexType: ComplexDataType): ComplexDataTypeResponse = {
    ComplexDataTypeResponse(
      definition = complexType.definition,
      parents = complexType.parents.map(fromDomain),
      typeArguments = complexType.typeArguments.map(PipelineDataTypeResponse.fromDomain)
    )
  }
}
