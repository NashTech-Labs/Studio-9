package cortex.task.tabular_data

import cortex.JsonSupport.SnakeJson
import cortex.TaskResult
import play.api.libs.json.Reads

case class TableImportResult(outputS3Path: String) extends TaskResult

object TableImportResult {
  implicit val TableImportResultReads: Reads[TableImportResult] = SnakeJson.reads[TableImportResult]
}
