package baile.routes.contract.tabular.model.summary

import play.api.libs.json.{ JsObject, Json, OWrites }

sealed trait TabularModelEvaluationSummaryResponse

case class LinearModelEvaluationSummaryResponse(
  rmse: Double,
  r2: Double,
  MAPE: Double
) extends TabularModelEvaluationSummaryResponse

case class LogisticModelEvaluationSummaryResponse(
  KS: Option[Double],
  confusionMatrix: Seq[ConfusionMatrixRowResponse]
) extends TabularModelEvaluationSummaryResponse

object TabularModelEvaluationSummaryResponse {

  implicit val TabularModelEvaluationSummaryResponseWrites: OWrites[TabularModelEvaluationSummaryResponse] =
    new OWrites[TabularModelEvaluationSummaryResponse] {
      override def writes(o: TabularModelEvaluationSummaryResponse): JsObject = o match {
        case linear: LinearModelEvaluationSummaryResponse =>
          Json.writes[LinearModelEvaluationSummaryResponse].writes(linear)
        case logistic: LogisticModelEvaluationSummaryResponse =>
          Json.writes[LogisticModelEvaluationSummaryResponse].writes(logistic)
      }
    }


}
