package baile.routes.contract.pipeline

import baile.domain.asset.AssetReference
import baile.domain.pipeline.pipeline.{ GenericExperimentPipeline => DomainGenericExperimentPipeline }
import baile.routes.contract.experiment.ExperimentPipeline
import baile.routes.contract.asset.AssetReferenceFormat
import play.api.libs.json.{ Json, OFormat }

case class GenericExperimentPipeline(
  steps: Seq[PipelineStep],
  assets: Option[Seq[AssetReference]]
) extends ExperimentPipeline {

  def toDomain: DomainGenericExperimentPipeline =
    DomainGenericExperimentPipeline(
      steps.map(_.toDomain),
      Seq.empty
    )

}

object GenericExperimentPipeline {

  def fromDomain(in: DomainGenericExperimentPipeline): GenericExperimentPipeline = GenericExperimentPipeline(
    in.steps.map(PipelineStep.fromDomain),
    Some(in.assets)
  )

  implicit val GenericExperimentPipelineFormat: OFormat[GenericExperimentPipeline] =
    Json.format[GenericExperimentPipeline]

}
