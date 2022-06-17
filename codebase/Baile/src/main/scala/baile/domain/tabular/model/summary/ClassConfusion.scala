package baile.domain.tabular.model.summary

final case class ClassConfusion(
  className: String,
  truePositive: Int,
  trueNegative: Int,
  falsePositive: Int,
  falseNegative: Int
)
