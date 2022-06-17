package cortex.task.transform.exporter.redshift

import cortex.TaskResult
import cortex.task.task_creators.TransformTaskCreator
import cortex.task.transform.exporter.redshift.RedshiftExporterParams.RedshiftExporterTaskParams

class RedshiftExporterModule extends TransformTaskCreator[RedshiftExporterTaskParams, TaskResult.Empty] {

  override val dockerImage: String = "deepcortex/cortex-tasks-sklearn"
  override val module: String = "redshift_exporter"

}
