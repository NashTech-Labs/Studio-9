package baile.routes.contract.experiment

import baile.domain.cv.result.CVTLTrainResult
import baile.domain.experiment.result.ExperimentResult
import baile.domain.pipeline.result.GenericExperimentResult
import baile.domain.tabular.result.TabularTrainResult
import baile.routes.contract.cv.CVTLTrainResultResponse
import baile.routes.contract.pipeline.GenericExperimentResultResponse
import baile.routes.contract.tabular.TabularTrainResultResponse
import play.api.libs.json._

trait ExperimentResultResponse

object ExperimentResultResponse {

  def fromDomain(in: ExperimentResult): ExperimentResultResponse = in match {
    case cvtlTrainResult: CVTLTrainResult => CVTLTrainResultResponse.fromDomain(cvtlTrainResult)
    case tabularTrainResult: TabularTrainResult => TabularTrainResultResponse.fromDomain(tabularTrainResult)
    case genericExperimentResult: GenericExperimentResult =>
      GenericExperimentResultResponse.fromDomain(genericExperimentResult)
  }

  implicit val ExperimentResultResponseWrites: Writes[ExperimentResultResponse] = new Writes[ExperimentResultResponse] {

    override def writes(result: ExperimentResultResponse): JsValue = result match {
      case result: CVTLTrainResultResponse => Json.toJsObject[CVTLTrainResultResponse](result)
      case result: TabularTrainResultResponse => Json.toJsObject[TabularTrainResultResponse](result)
      case result: GenericExperimentResultResponse => Json.toJsObject[GenericExperimentResultResponse](result)
    }

  }

}
