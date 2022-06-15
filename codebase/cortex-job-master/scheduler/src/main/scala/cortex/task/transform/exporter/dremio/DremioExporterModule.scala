package cortex.task.transform.exporter.dremio

import cortex.TaskResult
import cortex.task.task_creators.TransformTaskCreator
import cortex.task.transform.exporter.dremio.DremioExporterParams.DremioExporterTaskParams

class DremioExporterModule extends TransformTaskCreator[DremioExporterTaskParams, TaskResult.Empty] {

  override val dockerImage: String = "deepcortex/cortex-tasks-sklearn"
  override val module: String = "dremio_exporter"

}
