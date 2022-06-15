package baile.domain.table

case class CategoricalHistogram(
  rows: Seq[CategoricalHistogramRow]
)

case class CategoricalHistogramRow(
  value: Option[String],
  count: Long
)
