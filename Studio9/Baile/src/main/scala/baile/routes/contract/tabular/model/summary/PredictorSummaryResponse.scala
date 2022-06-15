package baile.routes.contract.tabular.model.summary

import baile.domain.tabular.model.summary.{
  ParametricModelPredictorSummary,
  PredictorSummary,
  TreeModelPredictorSummary
}
import play.api.libs.json.{ JsObject, Json, OWrites }

sealed trait PredictorSummaryResponse

case class ParametricModelPredictorSummaryResponse(
  name: String,
  estimate: Double,
  stdError: Double,
  tvalue: Double,
  pvalue: Double
) extends PredictorSummaryResponse

case class TreeModelPredictorSummaryResponse(
  name: String,
  importance: Double
) extends PredictorSummaryResponse

object PredictorSummaryResponse {

  implicit val ParametricModelPredictorSummaryResponseWrites: OWrites[ParametricModelPredictorSummaryResponse] =
    Json.writes[ParametricModelPredictorSummaryResponse]

  implicit val TreeModelPredictorSummaryResponseWrites: OWrites[TreeModelPredictorSummaryResponse] =
    Json.writes[TreeModelPredictorSummaryResponse]

  implicit val PredictorSummaryResponseWrites: OWrites[PredictorSummaryResponse] =
    new OWrites[PredictorSummaryResponse] {
      override def writes(summary: PredictorSummaryResponse): JsObject =
        summary match {
          case summary: ParametricModelPredictorSummaryResponse =>
            ParametricModelPredictorSummaryResponseWrites.writes(summary)
          case summary: TreeModelPredictorSummaryResponse =>
            TreeModelPredictorSummaryResponseWrites.writes(summary)
        }
    }

  def fromDomain(predictorSummary: PredictorSummary): PredictorSummaryResponse = predictorSummary match {
    case summary: ParametricModelPredictorSummary =>
      ParametricModelPredictorSummaryResponse(
        name = summary.name,
        estimate = summary.coefficient,
        stdError = summary.stdErr,
        tvalue = summary.tValue,
        pvalue = summary.pValue
      )
    case TreeModelPredictorSummary(name, importance) =>
      TreeModelPredictorSummaryResponse(
        name = name,
        importance = importance
      )
  }

}
