package cortex.task.tabular_data.tabularpipeline

import cortex.task.tabular_data.tabularpipeline.TabularPipelineParams._
import cortex.task.task_creators._

class TabularPipelineModule(baseOutputPath: String) extends TrainPredictTaskCreator[TabularTrainPredictParams, TabularTrainPredictResult]
  with TrainTaskCreator[TabularTrainParams, TabularTrainResult]
  with ScoreTaskCreator[TabularScoreParams, TabularScoreResult]
  with PredictTaskCreator[TabularPredictParams, TabularPredictResult]
  with EvaluateTaskCreator[TabularEvaluateParams, TabularEvaluateResult] {

  override val dockerImage: String = "deepcortex/cortex-tasks-gpu"
  override val module: String = "tabular_pipeline"

}
