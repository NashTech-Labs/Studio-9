package baile.routes.contract.cv

import baile.domain.cv.result.CVTLTrainResult
import baile.routes.contract.experiment.ExperimentResultResponse
import play.api.libs.json.{ Json, OWrites }

case class CVTLTrainResultResponse(
  step1: CVTLTrainStepResultResponse,
  step2: Option[CVTLTrainStepResultResponse]
) extends ExperimentResultResponse

object CVTLTrainResultResponse {

  def fromDomain(in: CVTLTrainResult): CVTLTrainResultResponse =
    CVTLTrainResultResponse(
      CVTLTrainStepResultResponse.fromDomain(in.stepOne),
      in.stepTwo.map(CVTLTrainStepResultResponse.fromDomain)
    )

  implicit val CVTLTrainResultResponseWrites: OWrites[CVTLTrainResultResponse] =
    Json.writes[CVTLTrainResultResponse]

}
