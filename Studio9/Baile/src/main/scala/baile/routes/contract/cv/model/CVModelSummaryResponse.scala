package baile.routes.contract.cv.model

import baile.domain.cv.model.CVModelSummary
import baile.routes.contract.common.ConfusionMatrixCellResponse
import play.api.libs.json.{ Json, OWrites }

case class CVModelSummaryResponse(
  labels: Seq[String],
  confusionMatrix: Option[Seq[ConfusionMatrixCellResponse]],
  mAP: Option[Double] = None,
  reconstructionLoss: Option[Double] = None
)

object CVModelSummaryResponse {

  def fromDomain(summary: CVModelSummary): CVModelSummaryResponse = CVModelSummaryResponse(
    labels = summary.labels,
    confusionMatrix = summary.confusionMatrix.map(_.map { cell =>
      ConfusionMatrixCellResponse(
        actual = cell.actualLabel,
        predicted = cell.predictedLabel,
        count = cell.count
      )
    }),
    mAP = summary.mAP,
    reconstructionLoss = summary.reconstructionLoss
  )

  implicit val CVModelSummaryResponseWrites: OWrites[CVModelSummaryResponse] = Json.writes[CVModelSummaryResponse]
}
