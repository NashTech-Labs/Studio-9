package baile.domain.table

case class NumericalHistogram(
  rows: Seq[NumericalHistogramRow]
)

case class NumericalHistogramRow(
  min: Double,
  max: Double,
  count: Long
)
