package baile.routes.contract.pipeline.operator

import baile.daocommons.WithId
import baile.domain.dcproject.DCProjectPackage
import baile.domain.pipeline.PipelineOperator
import baile.routes.contract.common.Version
import play.api.libs.json.{ Json, OWrites }

case class PipelineOperatorResponse(
  id: String,
  name: String,
  description: Option[String],
  category: Option[String],
  className: String,
  moduleName: String,
  packageName: String,
  packageVersion: Option[Version],
  inputs: Seq[PipelineOperatorInputResponse],
  outputs: Seq[PipelineOperatorOutputResponse],
  params: Seq[ParameterDefinitionResponse]
)

object PipelineOperatorResponse {
  implicit val PipelineOperatorResponseWrites: OWrites[PipelineOperatorResponse] = Json.writes[PipelineOperatorResponse]

  def fromDomain(
    pipelineOperator: WithId[PipelineOperator],
    packageInfo: WithId[DCProjectPackage]
  ): PipelineOperatorResponse = {
    PipelineOperatorResponse(
      id = pipelineOperator.id,
      name = pipelineOperator.entity.name,
      category = pipelineOperator.entity.category,
      description = pipelineOperator.entity.description,
      className = pipelineOperator.entity.className,
      moduleName = pipelineOperator.entity.moduleName,
      packageName = packageInfo.entity.name,
      packageVersion = packageInfo.entity.version.map(Version.fromDomain),
      inputs = pipelineOperator.entity.inputs.map { input =>
        PipelineOperatorInputResponse.fromDomain(input)
      },
      outputs = pipelineOperator.entity.outputs.map { operator =>
        PipelineOperatorOutputResponse.fromDomain(operator)
      },
      params = pipelineOperator.entity.params.map(ParameterDefinitionResponse.fromDomain)
    )
  }

}
