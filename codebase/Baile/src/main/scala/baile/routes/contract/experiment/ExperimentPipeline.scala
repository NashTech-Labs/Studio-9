package baile.routes.contract.experiment

import baile.domain.cv.pipeline.{ CVTLTrainPipeline => DomainCVTLTrainPipeline }
import baile.domain.experiment.pipeline.{ ExperimentPipeline => DomainExperimentPipeline }
import baile.domain.tabular.pipeline.{ TabularTrainPipeline => DomainTabularTrainPipeline }
import baile.domain.pipeline.pipeline.{ GenericExperimentPipeline => DomainGenericExperimentPipeline }
import baile.routes.contract.cv.CVTLTrainPipeline
import baile.routes.contract.pipeline.GenericExperimentPipeline
import baile.routes.contract.tabular.TabularTrainPipeline
import play.api.libs.json._

trait ExperimentPipeline {
  def toDomain: DomainExperimentPipeline
}

object ExperimentPipeline {

  def fromDomain(in: DomainExperimentPipeline): ExperimentPipeline = in match {
    case cvtlTrainPipeline: DomainCVTLTrainPipeline => CVTLTrainPipeline.fromDomain(cvtlTrainPipeline)
    case tabularTrainPipeline: DomainTabularTrainPipeline => TabularTrainPipeline.fromDomain(tabularTrainPipeline)
    case genericExperimentPipeline: DomainGenericExperimentPipeline =>
      GenericExperimentPipeline.fromDomain(genericExperimentPipeline)
  }

  implicit val ExperimentPipelineWrites: Writes[ExperimentPipeline] = new Writes[ExperimentPipeline] {
    override def writes(pipeline: ExperimentPipeline): JsValue = pipeline match {
      case tlTrainPipeline: CVTLTrainPipeline => Json.toJsObject[CVTLTrainPipeline](tlTrainPipeline)
      case tabularTrainPipeline: TabularTrainPipeline => Json.toJsObject[TabularTrainPipeline](tabularTrainPipeline)
      case genericExperimentPipeline: GenericExperimentPipeline =>
        Json.toJsObject[GenericExperimentPipeline](genericExperimentPipeline)
    }
  }

}
