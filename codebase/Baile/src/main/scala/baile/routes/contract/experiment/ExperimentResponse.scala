package baile.routes.contract.experiment

import java.time.Instant

import baile.daocommons.WithId
import baile.domain.cv.pipeline.{ CVTLTrainPipeline => DomainCVTLTrainPipeline }
import baile.domain.experiment.{ Experiment, ExperimentStatus }
import baile.domain.tabular.pipeline.{ TabularTrainPipeline => DomainTabularTrainPipeline }
import baile.domain.pipeline.pipeline.{ GenericExperimentPipeline => DomainGenericExperimentPipeline }
import play.api.libs.json.{ Json, OWrites }

case class ExperimentResponse(
  id: String,
  name: String,
  ownerId: String,
  created: Instant,
  updated: Instant,
  description: Option[String],
  status: ExperimentStatus,
  `type`: ExperimentType
)

object ExperimentResponse {

  implicit val ExperimentResponseWrites: OWrites[ExperimentResponse] = Json.writes[ExperimentResponse]

  def fromDomain(in: WithId[Experiment]): ExperimentResponse = in match {
    case WithId(experiment, id) =>
      ExperimentResponse(
        id = id,
        name = experiment.name,
        ownerId = experiment.ownerId.toString,
        created = experiment.created,
        updated = experiment.updated,
        description = experiment.description,
        status = experiment.status,
        `type` = experiment.pipeline match {
          case _: DomainCVTLTrainPipeline => ExperimentType.CVTLTrain
          case _: DomainTabularTrainPipeline => ExperimentType.TabularTrain
          case _: DomainGenericExperimentPipeline => ExperimentType.GenericExperiment
        }
      )
  }

}
