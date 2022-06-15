package cortex.task.transform.copier

import cortex.task.task_creators.TransformTaskCreator
import cortex.task.transform.copier.CopierParams.{ CopierTaskParams, CopierTaskResult }

class CopierModule extends TransformTaskCreator[CopierTaskParams, CopierTaskResult] {
  override val dockerImage: String = "deepcortex/cortex-tasks-sklearn"
  override val module: String = "copier"
}
