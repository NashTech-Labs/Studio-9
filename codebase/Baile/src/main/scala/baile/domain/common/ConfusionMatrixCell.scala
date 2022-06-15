package baile.domain.common

case class ConfusionMatrixCell(
  actualLabel: Option[Int],
  predictedLabel: Option[Int],
  count: Int
)
