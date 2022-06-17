package cortex.task.tabular_data

import cortex.task.tabular_data.TabularModelImportParams.{ TabularModelImportTaskParams, TabularModelImportTaskResult }
import cortex.task.task_creators.TransformTaskCreator

class TabularModelImportModule extends TransformTaskCreator[TabularModelImportTaskParams, TabularModelImportTaskResult] {

  override val dockerImage: String = "deepcortex/cortex-tasks-gpu"
  override val module: String = "import_tabular_model"

}
