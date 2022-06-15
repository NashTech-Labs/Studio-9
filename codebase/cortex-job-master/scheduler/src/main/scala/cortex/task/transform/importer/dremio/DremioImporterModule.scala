package cortex.task.transform.importer.dremio

import cortex.task.task_creators.TransformTaskCreator
import DremioImporterParams.DremioImporterTaskParams
import cortex.task.tabular_data.TableImportResult

class DremioImporterModule extends TransformTaskCreator[DremioImporterTaskParams, TableImportResult] {

  override val dockerImage: String = "deepcortex/cortex-tasks-sklearn"
  override val module: String = "dremio_importer"

}
