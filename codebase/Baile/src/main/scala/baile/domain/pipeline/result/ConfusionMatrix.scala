package baile.domain.pipeline.result

import baile.domain.common.ConfusionMatrixCell

case class ConfusionMatrix(
  confusionMatrixCells: Seq[ConfusionMatrixCell],
  labels: Seq[String]
) extends PipelineOperatorApplicationSummary
