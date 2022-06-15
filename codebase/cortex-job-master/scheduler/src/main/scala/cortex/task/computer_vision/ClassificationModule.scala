package cortex.task.computer_vision

import cortex.task.computer_vision.ClassificationParams._
import cortex.task.task_creators._

class ClassificationModule
  extends TrainTaskCreator[CVTrainTaskParams, CVTrainTaskResult]
  with ScoreTaskCreator[CVScoreTaskParams, CVScoreTaskResult]
  with PredictTaskCreator[CVPredictTaskParams, CVPredictTaskResult] {

  override val dockerImage: String = "deepcortex/cortex-tasks-gpu"
  override val module: String = "cv_classifier"
}
