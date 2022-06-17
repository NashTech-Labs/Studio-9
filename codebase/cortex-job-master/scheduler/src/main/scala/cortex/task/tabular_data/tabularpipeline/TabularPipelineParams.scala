package cortex.task.tabular_data.tabularpipeline

import cortex.JsonSupport._
import cortex.task.column.ColumnDataType
import cortex.task.common.ClassReference
import cortex.task.{ HyperParam, StorageAccessParams }
import cortex.task.tabular_data.tabularpipeline.TabularModelSummary.TabularModelSummary
import cortex.task.tabular_data.{ AllowedModelPrimitive, AllowedTaskType, ModelReference }
import cortex.{ TaskParams, TaskResult }
import play.api.libs.functional.syntax._
import play.api.libs.json._

object TabularPipelineParams {

  /**
   * Train
   */

  case class TabularTrainParams(
      trainInputPaths:       Seq[String],
      taskType:              AllowedTaskType,
      modelPrimitive:        AllowedModelPrimitive,
      weightsCol:            Option[String],
      response:              String,
      numericalPredictors:   Seq[String],
      categoricalPredictors: Seq[String],
      hyperparamsDict:       Map[String, HyperParam],
      storageAccessParams:   StorageAccessParams,
      mvhModelId:            String,
      modelsBasePath:        String,
      action:                String                  = "train"
  ) extends TaskParams

  case class TabularTrainResult(
      taskId:         String,
      modelReference: ModelReference,
      summaryStats:   TabularModelSummary
  ) extends TaskResult

  /**
   * Train predict
   */

  case class TabularTrainPredictParams(
      trainInputPaths:       Seq[String],
      taskType:              AllowedTaskType,
      modelPrimitive:        AllowedModelPrimitive,
      weightsCol:            Option[String],
      response:              String,
      numericalPredictors:   Seq[String],
      categoricalPredictors: Seq[String],
      predictColName:        String,
      probabilityColPrefix:  Option[String],
      hyperparamsDict:       Map[String, HyperParam],
      storageAccessParams:   StorageAccessParams,
      mvhModelId:            String,
      modelsBasePath:        String,
      predictPath:           String,
      action:                String                  = "train_predict"
  ) extends TaskParams

  case class TabularTrainPredictResult(
      taskId:              String,
      modelReference:      ModelReference,
      predictPath:         String,
      headerColNames:      Seq[String],
      probabilityColNames: List[(String, String)],
      summaryStats:        TabularModelSummary
  ) extends TaskResult

  /**
   * Predict
   */

  case class TabularPredictParams(
      validateInputPaths:  Seq[String],
      modelId:             String,
      columnsMapping:      Map[String, String],
      predictColName:      String,
      probabilityColNames: List[(String, String)],
      storageAccessParams: StorageAccessParams,
      classReference:      ClassReference,
      modelsBasePath:      String,
      predictPath:         String,
      action:              String                 = "predict"
  ) extends TaskParams

  case class TabularPredictResult(
      taskId:             String,
      predictPath:        String,
      headerColNames:     Seq[String],
      responseColumnType: ColumnDataType
  ) extends TaskResult

  /**
   * Score
   */

  case class TabularScoreParams(
      validateInputPaths:  Seq[String],
      modelId:             String,
      storageAccessParams: StorageAccessParams,
      modelsBasePath:      String,
      action:              String              = "score"
  ) extends TaskParams

  case class TabularScoreResult(
      taskId:      String,
      scoreOutput: Double
  ) extends TaskResult

  /**
   * Evaluate
   */

  case class TabularEvaluateParams(
      validateInputPaths:  Seq[String],
      modelId:             String,
      weightsCol:          Option[String],
      columnsMapping:      Map[String, String],
      predictColName:      String,
      probabilityColNames: List[(String, String)],
      storageAccessParams: StorageAccessParams,
      classReference:      ClassReference,
      modelsBasePath:      String,
      predictPath:         String,
      action:              String                 = "evaluate"
  ) extends TaskParams

  case class TabularEvaluateResult(
      taskId:             String,
      scoreOutput:        Double,
      predictPath:        String,
      headerColNames:     Seq[String],
      summaryStats:       TabularModelSummary,
      responseColumnType: ColumnDataType
  ) extends TaskResult

  private implicit val hyperParamWrites: Writes[HyperParam] = HyperParam.HyperParamWrites
  implicit val tabularTrainParamsWrites: Writes[TabularTrainParams] = SnakeJson.writes[TabularTrainParams]
  implicit val tabularTrainResultReads: Reads[TabularTrainResult] = SnakeJson.reads[TabularTrainResult]

  implicit val tabularTrainPredictParamsWrites: Writes[TabularTrainPredictParams] = SnakeJson.writes[TabularTrainPredictParams]
  implicit val tabularTrainPredictResultReads: Reads[TabularTrainPredictResult] = SnakeJson.reads[TabularTrainPredictResult]

  implicit val tabularPredictParamsWrites: Writes[TabularPredictParams] = SnakeJson.writes[TabularPredictParams]
  implicit val tabularPredictResultReads: Reads[TabularPredictResult] = SnakeJson.reads[TabularPredictResult]

  implicit val tabularScoreParamsWrites: Writes[TabularScoreParams] = SnakeJson.writes[TabularScoreParams]
  implicit val tabularScoreResultReads: Reads[TabularScoreResult] = SnakeJson.reads[TabularScoreResult]

  implicit val tabularEvaluateParamsWrites: Writes[TabularEvaluateParams] = SnakeJson.writes[TabularEvaluateParams]
  implicit val tabularEvaluateResultReads: Reads[TabularEvaluateResult] = SnakeJson.reads[TabularEvaluateResult]
}

object TabularModelSummary {

  trait TabularModelSummary {
    def formula: Option[String]
    def variableInfo: Option[Seq[VariableInfo]]
  }

  //TODO merge confusionMatrix and labels
  case class TabularClassifierSummary(
      //should be 4*labels size, for each label will be 4 values [tp, fp, fn, tn]
      confusionMatrix:         Seq[Int],
      labels:                  Seq[String],
      binaryClassifierSummary: Option[TabularBinaryClassifierSummary],
      variableInfo:            Option[Seq[VariableInfo]],
      formula:                 Option[String]
  ) extends TabularModelSummary

  case class TabularRegressorSummary(
      rmse:         Double,
      rSquared:     Double,
      mape:         Double,
      formula:      Option[String],
      variableInfo: Option[Seq[VariableInfo]]
  ) extends TabularModelSummary

  case class VariableInfo(
      variableName: String,
      valueType:    String,
      value:        Double,
      stderr:       Option[Double],
      tvalue:       Option[Double],
      pvalue:       Option[Double]
  )

  case class TabularBinaryClassifierTrainSummary(
      rocFpr:    Seq[Double],
      rocTpr:    Seq[Double],
      auc:       Double,
      threshold: Double,
      f1Score:   Double,
      precision: Double,
      recall:    Double
  )

  case class TabularBinaryClassifierSummary(
      tabularBinaryClassifierTrainSummary: Option[TabularBinaryClassifierTrainSummary],
      ksStatistic:                         Double
  )

  private implicit val variableInfoReads: Reads[VariableInfo] = SnakeJson.reads[VariableInfo]
  private implicit val tabularClassifierSummaryReads: Reads[TabularClassifierSummary] = for {
    confusionMatrix <- (__ \ "confusion_matrix").read[Seq[Int]]
    labels <- (__ \ "labels").read[Seq[String]]
    binaryClassifierSummary <- ((
      (__ \ "roc_fpr").readNullable[Seq[Double]] and
      (__ \ "roc_tpr").readNullable[Seq[Double]] and
      (__ \ "auc").readNullable[Double] and
      (__ \ "threshold").readNullable[Double] and
      (__ \ "f1_score").readNullable[Double] and
      (__ \ "precision").readNullable[Double] and
      (__ \ "recall").readNullable[Double]
    )(
        (rocFprOpt, rocTprOpt, aucOpt, thresholdOpt, f1ScoreOpt, precisionOpt, recallOpt) =>
          for {
            rocFpr <- rocFprOpt
            rocTpr <- rocTprOpt
            auc <- aucOpt
            threshold <- thresholdOpt
            f1Score <- f1ScoreOpt
            precision <- precisionOpt
            recall <- recallOpt
          } yield TabularBinaryClassifierTrainSummary(rocFpr, rocTpr, auc, threshold, f1Score, precision, recall)
      ) and (__ \ "ks_statistic").readNullable[Double])(
        (tabularBinaryClassifierTrainSummary, ksStatisticOpt) =>
          for {
            ksStatistic <- ksStatisticOpt
          } yield TabularBinaryClassifierSummary(tabularBinaryClassifierTrainSummary, ksStatistic)
      )
    variableInfo <- (__ \ "variable_info").readNullable[Seq[VariableInfo]]
    formula <- (__ \ "formula").readNullable[String]
  } yield TabularClassifierSummary(confusionMatrix, labels, binaryClassifierSummary, variableInfo, formula)

  private implicit val tabularRegressorSummaryReads: Reads[TabularRegressorSummary] = SnakeJson.reads[TabularRegressorSummary]

  implicit val tabularModelSummaryReader: Reads[TabularModelSummary] =
    __.read[TabularClassifierSummary].map(_.asInstanceOf[TabularModelSummary]) orElse
      __.read[TabularRegressorSummary].map(_.asInstanceOf[TabularModelSummary])
}
