package baile.routes.contract.experiment

import baile.routes.contract.cv.CVTLTrainPipeline
import baile.routes.contract.pipeline.GenericExperimentPipeline
import baile.routes.contract.tabular.TabularTrainPipeline
import play.api.libs.functional.syntax._
import play.api.libs.json.{ Reads, __ }

case class ExperimentCreateRequest(
  name: Option[String],
  description: Option[String],
  pipeline: ExperimentPipeline
)

object ExperimentCreateRequest {
  implicit val ExperimentCreateRequestReads: Reads[ExperimentCreateRequest] = (
    (__ \ "name").readNullable[String] ~
    (__ \ "description").readNullable[String] ~
    (__ \ "type").read[ExperimentType].flatMap[ExperimentPipeline] {
      case ExperimentType.CVTLTrain =>
        (__ \ "pipeline").read[CVTLTrainPipeline].map(_.asInstanceOf[ExperimentPipeline])
      case ExperimentType.TabularTrain =>
        (__ \ "pipeline").read[TabularTrainPipeline].map(_.asInstanceOf[ExperimentPipeline])
      case ExperimentType.GenericExperiment =>
        (__ \ "pipeline").read[GenericExperimentPipeline].map(_.asInstanceOf[ExperimentPipeline])
    }
  )(ExperimentCreateRequest.apply _)
}
