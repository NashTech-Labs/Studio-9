package cortex.task.computer_vision

import cortex.task.computer_vision.ModelImportParams._
import cortex.task.task_creators.TransformTaskCreator

class ModelImportModule extends TransformTaskCreator[ModelImportTaskParams, ModelImportTaskResult] {

  override val dockerImage: String = "deepcortex/cortex-tasks-gpu"
  override val module: String = "import_model"

}
