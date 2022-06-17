package baile.domain.tabular.model.summary

sealed trait TabularModelTrainSummary

sealed trait TabularModelEvaluationSummary

final case class RegressionSummary(
  rmse: Double,
  r2: Double,
  mape: Double
) extends TabularModelTrainSummary with TabularModelEvaluationSummary

final case class ClassificationSummary(
  confusionMatrix: Seq[ClassConfusion]
) extends TabularModelTrainSummary with TabularModelEvaluationSummary

final case class BinaryClassificationEvaluationSummary(
  classificationSummary: ClassificationSummary,
  ks: Double
) extends TabularModelEvaluationSummary

final case class BinaryClassificationTrainSummary(
  evaluationSummary: BinaryClassificationEvaluationSummary,
  areaUnderROC: Double,
  rocValues: Seq[RocValue],
  f1Score: Double,
  precision: Double,
  recall: Double,
  threshold: Double
) extends TabularModelTrainSummary
