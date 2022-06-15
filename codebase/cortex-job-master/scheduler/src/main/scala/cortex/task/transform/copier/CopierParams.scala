package cortex.task.transform.copier

import cortex.{ TaskParams, TaskResult }
import cortex.task.StorageAccessParams
import cortex.JsonSupport.SnakeJson
import play.api.libs.json.{ OWrites, Reads }

object CopierParams {
  case class CopierTaskParams(
      inputPath:                 String,
      inputStorageAccessParams:  StorageAccessParams,
      outputPath:                String,
      outputStorageAccessParams: StorageAccessParams
  ) extends TaskParams

  case class CopierTaskResult(taskId: String, outputPath: String) extends TaskResult

  implicit val copierTaskParamsWrites: OWrites[CopierTaskParams] = SnakeJson.writes[CopierTaskParams]
  implicit val copierTaskResultReads: Reads[CopierTaskResult] = SnakeJson.reads[CopierTaskResult]
}
