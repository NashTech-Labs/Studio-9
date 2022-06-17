package baile.routes.contract.dcproject

import baile.daocommons.WithId
import baile.domain.dcproject.DCProjectPackage
import baile.domain.cv.model.tlprimitives.CVTLModelPrimitive
import baile.domain.pipeline.PipelineOperator
import baile.routes.contract.dcproject.cvtlmodelprimitives.CVTLModelPrimitiveResponse
import baile.routes.contract.pipeline.operator.PipelineOperatorResponse
import play.api.libs.json.{ Json, OWrites }

case class ExtendedDCProjectPackageResponse(
  packageResponse: DCProjectPackageResponse,
  primitives: Seq[CVTLModelPrimitiveResponse],
  pipelineOperators: Seq[PipelineOperatorResponse]
)

object ExtendedDCProjectPackageResponse {
  implicit val DCProjectPackageExtendedResponseWrites: OWrites[ExtendedDCProjectPackageResponse] =
    OWrites {
      case ExtendedDCProjectPackageResponse(projectPackage, primitives, operators) =>
        Json.toJsObject(projectPackage) +
          ("pipelineOperators" -> Json.toJson(operators)) +
          ("primitives" -> Json.toJson(primitives))
    }

  def fromDomain(
    projectPackage: WithId[DCProjectPackage],
    primitives: Seq[WithId[CVTLModelPrimitive]],
    pipelineOperators: Seq[WithId[PipelineOperator]]
  ): ExtendedDCProjectPackageResponse =
    ExtendedDCProjectPackageResponse(
      packageResponse = DCProjectPackageResponse.fromDomain(projectPackage),
      primitives = primitives.map { primitive =>
        CVTLModelPrimitiveResponse.fromDomain(primitive.entity)
      },
      pipelineOperators = pipelineOperators.map { operator =>
        PipelineOperatorResponse.fromDomain(operator, projectPackage)
      }
    )
}
