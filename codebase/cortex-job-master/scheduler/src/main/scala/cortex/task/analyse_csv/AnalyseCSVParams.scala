package cortex.task.analyse_csv

import cortex.JsonSupport.SnakeJson
import cortex.task.column.ColumnMapping
import cortex.task.StorageAccessParams
import cortex.task.transform.common.TableFileType
import cortex.{ TaskParams, TaskResult }
import play.api.libs.json._

object AnalyseCSVParams {

  case class AnalyseCSVTaskParams(
      inputParams: StorageAccessParams,
      filePath:    String,
      fileType:    TableFileType,
      delimiter:   String,
      nullValue:   String
  ) extends TaskParams

  case class AnalyseCSVTaskResult(
      columns: Seq[ColumnMapping]
  ) extends TaskResult

  implicit val analyseCSVTaskParamsWrites: OWrites[AnalyseCSVTaskParams] =
    SnakeJson.writes[AnalyseCSVTaskParams]
  implicit val analyseCSVTaskResultReads: Reads[AnalyseCSVTaskResult] =
    SnakeJson.reads[AnalyseCSVTaskResult]

}
