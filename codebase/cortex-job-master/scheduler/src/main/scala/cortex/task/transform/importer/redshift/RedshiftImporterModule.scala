package cortex.task.transform.importer.redshift

import cortex.task.tabular_data.TableImportResult
import cortex.task.task_creators.TransformTaskCreator
import cortex.task.transform.importer.redshift.RedshiftImporterParams._

class RedshiftImporterModule(
    s3Bucket:       String,
    baseOutputPath: String
) extends TransformTaskCreator[RedshiftImporterTaskParams, TableImportResult] {

  override val dockerImage: String = "deepcortex/cortex-tasks-sklearn"
  override val module: String = "redshift_importer"

}
