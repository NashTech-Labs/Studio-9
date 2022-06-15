package baile.services.cv.model.util.export.format.v1

import play.api.libs.json.{ Json, OFormat }

private[v1] case class ConfusionMatrixCell(
  actualLabel: Option[Int],
  predictedLabel: Option[Int],
  count: Int
)

private[v1] object ConfusionMatrixCell {

  implicit val ConfusionMatrixCellFormat: OFormat[ConfusionMatrixCell] =
    Json.format[ConfusionMatrixCell]

}
