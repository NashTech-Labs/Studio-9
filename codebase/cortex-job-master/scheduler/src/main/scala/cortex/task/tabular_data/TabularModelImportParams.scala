package cortex.task.tabular_data

import cortex.JsonSupport.SnakeJson
import cortex.task.StorageAccessParams.S3AccessParams
import cortex.task.common.ClassReference
import cortex.{ TaskParams, TaskResult }
import play.api.libs.json.{ OWrites, Reads }

object TabularModelImportParams {

  case class TabularModelImportTaskParams(
      classReference:      ClassReference,
      modelsBasePath:      String,
      modelPath:           String,
      storageAccessParams: S3AccessParams
  ) extends TaskParams

  case class TabularModelImportTaskResult(
      modelReference: ModelReference
  ) extends TaskResult

  implicit val modelImportTaskParamsWrites: OWrites[TabularModelImportTaskParams] = SnakeJson.writes[TabularModelImportTaskParams]
  implicit val modelImportTaskResultReads: Reads[TabularModelImportTaskResult] = SnakeJson.reads[TabularModelImportTaskResult]
}
