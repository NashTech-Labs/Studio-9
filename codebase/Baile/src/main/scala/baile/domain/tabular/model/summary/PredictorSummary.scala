package baile.domain.tabular.model.summary

sealed trait PredictorSummary {
  val name: String
}

final case class ParametricModelPredictorSummary(
  name: String,
  coefficient: Double,
  stdErr: Double,
  tValue: Double,
  pValue: Double
) extends PredictorSummary

final case class TreeModelPredictorSummary(name: String, importance: Double) extends PredictorSummary
