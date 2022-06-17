package cortex.task.computer_vision

import cortex.task.computer_vision.AutoencoderParams._
import cortex.task.task_creators.{ PredictTaskCreator, ScoreTaskCreator, TrainTaskCreator }

class StackedAutoencoderModule
  extends TrainTaskCreator[AutoencoderTrainTaskParams, AutoencoderTrainTaskResult]
  with PredictTaskCreator[AutoencoderPredictTaskParams, AutoencoderPredictTaskResult]
  with ScoreTaskCreator[AutoencoderScoreTaskParams, AutoencoderScoreTaskResult] {

  override val dockerImage: String = "deepcortex/cortex-tasks-gpu"
  override def module: String = "cv_stacked_autoencoder"
}
