package baile.routes.contract.pipeline

import baile.domain.pipeline.result.GenericExperimentResult
import baile.routes.contract.experiment.ExperimentResultResponse
import play.api.libs.json.{ Json, OWrites }

case class GenericExperimentResultResponse(
  steps: Seq[GenericExperimentStepResultResponse]
) extends ExperimentResultResponse

object GenericExperimentResultResponse {

  def fromDomain(in: GenericExperimentResult): GenericExperimentResultResponse =
    GenericExperimentResultResponse(
      in.steps.map(GenericExperimentStepResultResponse.fromDomain)
    )

  implicit val GenericExperimentResultResponseWrites: OWrites[GenericExperimentResultResponse] =
    Json.writes[GenericExperimentResultResponse]

}
