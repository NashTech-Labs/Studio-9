package baile.services.cv.model.util.export.format.v1

import play.api.libs.json.{ Json, OFormat }

private[v1] case class CVModelSummary(
  labels: Seq[String],
  confusionMatrix: Option[Seq[ConfusionMatrixCell]],
  mAP: Option[Double]
)

private[v1] object CVModelSummary {

  implicit val CVModelSummaryFormat: OFormat[CVModelSummary] = Json.format[CVModelSummary]

}
