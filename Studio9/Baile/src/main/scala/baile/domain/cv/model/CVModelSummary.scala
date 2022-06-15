package baile.domain.cv.model

import baile.domain.common.ConfusionMatrixCell

case class CVModelSummary(
  labels: Seq[String],
  confusionMatrix: Option[Seq[ConfusionMatrixCell]],
  mAP: Option[Double],
  reconstructionLoss: Option[Double]
)
