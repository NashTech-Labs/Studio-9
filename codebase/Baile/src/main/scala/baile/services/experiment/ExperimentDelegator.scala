package baile.services.experiment

import baile.domain.cv.pipeline.CVTLTrainPipeline
import baile.domain.experiment.pipeline.ExperimentPipeline
import baile.domain.pipeline.pipeline.GenericExperimentPipeline
import baile.domain.tabular.pipeline.TabularTrainPipeline
import baile.domain.usermanagement.User
import baile.services.cv.model.CVModelTrainPipelineHandler
import baile.services.experiment.PipelineHandler.{ CreateError, PipelineCreatedResult }
import baile.services.pipeline.GenericExperimentPipelineHandler
import baile.services.tabular.model.TabularTrainPipelineHandler

import scala.concurrent.{ ExecutionContext, Future }

// TODO should it be replaced by partial function composition?
class ExperimentDelegator(
  cvModelTrain: CVModelTrainPipelineHandler,
  tabularTrain: TabularTrainPipelineHandler,
  genericExperiment: GenericExperimentPipelineHandler
)(implicit ec: ExecutionContext) {

  def validateAndCreatePipeline(
    pipeline: ExperimentPipeline,
    experimentName: String,
    experimentDescription: Option[String]
  )(implicit user: User): Future[Either[CreateError, PipelineCreatedResult[_ <: ExperimentPipeline]]] =
    pipeline match {
      case cvtlPipeline: CVTLTrainPipeline =>
        cvModelTrain.validateAndCreatePipeline(cvtlPipeline, experimentName, experimentDescription)
      case tabularPipeline: TabularTrainPipeline =>
        tabularTrain.validateAndCreatePipeline(tabularPipeline, experimentName, experimentDescription)
      case genericExperimentPipeline: GenericExperimentPipeline =>
        genericExperiment.validateAndCreatePipeline(
          genericExperimentPipeline,
          experimentName,
          experimentDescription
        )
      case unknown =>
        Future.failed(new RuntimeException(s"Unsupported experiment pipeline: $unknown"))
    }

}
