package baile.routes.contract.experiment


import baile.daocommons.WithId
import baile.domain.experiment.Experiment
import baile.routes.contract.experiment.ExperimentResponse._
import play.api.libs.functional.syntax._
import play.api.libs.json.{ OWrites, __ }

case class ExperimentFullResponse(
  _base: ExperimentResponse,
  pipeline: ExperimentPipeline,
  result: Option[ExperimentResultResponse]
)

object ExperimentFullResponse {

  implicit val ExperimentFullResponseWrites: OWrites[ExperimentFullResponse] = (
    __.write[ExperimentResponse] ~
    (__ \ "pipeline").write[ExperimentPipeline] ~
    (__ \ "result").writeNullable[ExperimentResultResponse]
  ) {
    unlift(ExperimentFullResponse.unapply)
  }

  def fromDomain(in: WithId[Experiment]): ExperimentFullResponse = in match {
    case WithId(experiment, _) =>
      ExperimentFullResponse(
        _base = ExperimentResponse.fromDomain(in),
        pipeline = ExperimentPipeline.fromDomain(experiment.pipeline),
        result = experiment.result.map(ExperimentResultResponse.fromDomain)
      )
  }

}
