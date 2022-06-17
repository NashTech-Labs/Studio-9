package cortex.task.computer_vision

import cortex.task.computer_vision.CustomModelParams._
import cortex.task.task_creators._

class CustomModelModule
  extends ScoreTaskCreator[ScoreTaskParams, ScoreTaskResult]
  with PredictTaskCreator[PredictTaskParams, PredictTaskResult] {

  override val dockerImage: String = "deepcortex/cortex-tasks-gpu"
  override val module: String = "cv_custom_model"

}
