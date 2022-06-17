package cortex.task.project_packager

import cortex.task.project_packager.ProjectPackagerParams.{ ProjectPackagerTaskParams, ProjectPackagerTaskResult }
import cortex.task.task_creators.TransformTaskCreator

class ProjectPackagerModule extends TransformTaskCreator[ProjectPackagerTaskParams, ProjectPackagerTaskResult] {
  override val dockerImage: String = "deepcortex/cortex-tasks-gpu"
  override val module: String = "project_packager"
}
