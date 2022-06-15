package cortex.task.computer_vision

import cortex.task.computer_vision.LocalizationParams._
import cortex.task.task_creators._
import play.api.libs.json.{ Reads, Writes }

class LocalizationModule
  extends TrainTaskCreator[TrainTaskParams, TrainTaskResult]
  with ScoreTaskCreator[ScoreTaskParams, ScoreTaskResult]
  with PredictTaskCreator[PredictTaskParams, PredictTaskResult] {

  override val dockerImage: String = "deepcortex/cortex-tasks-gpu"
  override val module: String = "cv_localizer"

  def composeVideoTask(
    id:       String,
    jobId:    String,
    taskPath: String,
    params:   ComposeVideoTaskParams,
    cpus:     Double,
    memory:   Double,
    gpus:     Int                    = 0
  )(implicit reads: Reads[ComposeVideoTaskResult], writes: Writes[ComposeVideoTaskParams]): GenericTask[ComposeVideoTaskResult, ComposeVideoTaskParams] = {
    GenericTask[ComposeVideoTaskResult, ComposeVideoTaskParams](id, jobId, params, cpus, memory, module, taskPath, dockerImage, gpus)(reads, writes)
  }

}
