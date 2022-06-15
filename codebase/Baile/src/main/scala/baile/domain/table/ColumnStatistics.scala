package baile.domain.table

sealed trait ColumnStatistics

case class NumericalStatistics(
  min: Double,
  max: Double,
  avg: Double,
  std: Double,
  stdPopulation: Double,
  mean: Double,
  numericalHistogram: NumericalHistogram
) extends ColumnStatistics

case class CategoricalStatistics(
  uniqueValuesCount: Long,
  categoricalHistogram: CategoricalHistogram
) extends ColumnStatistics
